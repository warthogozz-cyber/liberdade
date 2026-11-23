package br.com.liberdadefamilia.privacy.internal;

import br.com.liberdadefamilia.audit.AuditEntry;
import br.com.liberdadefamilia.audit.AuditTrail;
import br.com.liberdadefamilia.billing.Plans;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records that a person accepted the version in force of the terms of use and the privacy policy. */
@Service
class DocumentService {

    private static final String OUTDATED_DETAIL = "Os termos mudaram. Leia a versão atual e aceite de novo.";

    private final ConsentRepository consents;
    private final AuditTrail trail;
    private final Plans plans;

    DocumentService(ConsentRepository consents, AuditTrail trail, Plans plans) {
        this.consents = consents;
        this.trail = trail;
        this.plans = plans;
    }

    /** The versions in force: the texts with billing once billing is on (D17). */
    LegalVersions current() {
        Map<ConsentPurpose, String> current = inForce();
        return new LegalVersions(current.get(ConsentPurpose.TERMS), current.get(ConsentPurpose.PRIVACY));
    }

    @Transactional(readOnly = true)
    List<DocumentStatus> list(UUID userId) {
        Map<ConsentPurpose, String> current = inForce();
        Map<ConsentPurpose, ConsentRepository.Row> open = openAcceptances(userId);
        List<DocumentStatus> statuses = new ArrayList<>();
        for (ConsentPurpose purpose : LegalDocuments.LISTED) {
            ConsentRepository.Row accepted = open.get(purpose);
            String currentVersion = current.get(purpose);
            String acceptedVersion = accepted == null ? null : accepted.documentVersion();
            statuses.add(new DocumentStatus(
                    purpose,
                    currentVersion,
                    acceptedVersion,
                    accepted == null ? null : accepted.grantedAt(),
                    acceptanceRequired(currentVersion, acceptedVersion)));
        }
        return statuses;
    }

    /** Accepts both documents at the versions in force; one that is already accepted at that version stays as is. */
    @Transactional
    void accept(UUID userId, AcceptDocumentsRequest request) {
        Map<ConsentPurpose, String> sent =
                Map.of(ConsentPurpose.TERMS, request.terms(), ConsentPurpose.PRIVACY, request.privacy());
        Map<ConsentPurpose, String> inForce = inForce();
        for (ConsentPurpose purpose : LegalDocuments.LISTED) {
            if (!inForce.get(purpose).equals(sent.get(purpose))) {
                throw new PrivacyRejectedException(409, "documents.version-outdated", OUTDATED_DETAIL);
            }
        }
        // One accept at a time per person; the one that waited re-reads below and finds nothing left to do.
        consents.lockAcceptancesOf(userId);
        Map<ConsentPurpose, ConsentRepository.Row> open = openAcceptances(userId);
        boolean recorded = false;
        for (ConsentPurpose purpose : LegalDocuments.LISTED) {
            String current = inForce.get(purpose);
            ConsentRepository.Row accepted = open.get(purpose);
            if (accepted != null && accepted.documentVersion().equals(current)) {
                continue;
            }
            consents.supersedeOpenPersonal(userId, purpose);
            consents.insert(userId, null, purpose, current, userId);
            recorded = true;
        }
        if (recorded) {
            trail.record(new AuditEntry(null, userId, "documents.accepted", "USER", userId, Map.of()));
        }
    }

    /** Whether the accepted version no longer covers the current one: it is neither the same nor the one it keeps. */
    private static boolean acceptanceRequired(String currentVersion, String acceptedVersion) {
        return acceptedVersion == null
                || !(acceptedVersion.equals(currentVersion)
                        || acceptedVersion.equals(LegalDocuments.KEEPS_ACCEPTANCE.get(currentVersion)));
    }

    private Map<ConsentPurpose, String> inForce() {
        return LegalDocuments.current(plans.legalSetEnabled());
    }

    private Map<ConsentPurpose, ConsentRepository.Row> openAcceptances(UUID userId) {
        Map<ConsentPurpose, ConsentRepository.Row> open = new EnumMap<>(ConsentPurpose.class);
        consents.openPersonal(userId).forEach(row -> open.put(row.purpose(), row));
        return open;
    }
}
