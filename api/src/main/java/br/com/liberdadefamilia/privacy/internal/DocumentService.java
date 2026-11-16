package br.com.liberdadefamilia.privacy.internal;

import br.com.liberdadefamilia.audit.AuditEntry;
import br.com.liberdadefamilia.audit.AuditTrail;
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

    DocumentService(ConsentRepository consents, AuditTrail trail) {
        this.consents = consents;
        this.trail = trail;
    }

    @Transactional(readOnly = true)
    List<DocumentStatus> list(UUID userId) {
        Map<ConsentPurpose, ConsentRepository.Row> open = openAcceptances(userId);
        List<DocumentStatus> statuses = new ArrayList<>();
        for (ConsentPurpose purpose : LegalDocuments.LISTED) {
            ConsentRepository.Row accepted = open.get(purpose);
            statuses.add(new DocumentStatus(
                    purpose,
                    LegalDocuments.CURRENT.get(purpose),
                    accepted == null ? null : accepted.documentVersion(),
                    accepted == null ? null : accepted.grantedAt()));
        }
        return statuses;
    }

    /** Accepts both documents at the versions in force; one that is already accepted at that version stays as is. */
    @Transactional
    void accept(UUID userId, AcceptDocumentsRequest request) {
        Map<ConsentPurpose, String> sent =
                Map.of(ConsentPurpose.TERMS, request.terms(), ConsentPurpose.PRIVACY, request.privacy());
        for (ConsentPurpose purpose : LegalDocuments.LISTED) {
            if (!LegalDocuments.CURRENT.get(purpose).equals(sent.get(purpose))) {
                throw new PrivacyRejectedException(409, "documents.version-outdated", OUTDATED_DETAIL);
            }
        }
        // One accept at a time per person; the one that waited re-reads below and finds nothing left to do.
        consents.lockAcceptancesOf(userId);
        Map<ConsentPurpose, ConsentRepository.Row> open = openAcceptances(userId);
        boolean recorded = false;
        for (ConsentPurpose purpose : LegalDocuments.LISTED) {
            String current = LegalDocuments.CURRENT.get(purpose);
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

    private Map<ConsentPurpose, ConsentRepository.Row> openAcceptances(UUID userId) {
        Map<ConsentPurpose, ConsentRepository.Row> open = new EnumMap<>(ConsentPurpose.class);
        consents.openPersonal(userId).forEach(row -> open.put(row.purpose(), row));
        return open;
    }
}
