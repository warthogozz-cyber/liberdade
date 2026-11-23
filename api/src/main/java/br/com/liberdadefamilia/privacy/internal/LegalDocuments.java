package br.com.liberdadefamilia.privacy.internal;

import java.util.List;
import java.util.Map;

/**
 * The version of each legal document in force. Publishing a new text means adding its version here. The texts with
 * billing (the Founder presale, Asaas as operator) apply only where billing is on, so selling and the terms that cover
 * it never fall out of step (D17).
 */
final class LegalDocuments {

    private static final Map<ConsentPurpose, String> WITHOUT_BILLING =
            Map.of(ConsentPurpose.TERMS, "terms-2026-11", ConsentPurpose.PRIVACY, "privacy-2026-11.1");

    private static final Map<ConsentPurpose, String> WITH_BILLING =
            Map.of(ConsentPurpose.TERMS, "terms-2026-12", ConsentPurpose.PRIVACY, "privacy-2026-12.1");

    /**
     * A version that only adds to what the person already accepted keeps that acceptance: it maps to the version it
     * continues, and anyone who accepted that one is told about the change without being asked to accept again (D18).
     */
    static final Map<String, String> KEEPS_ACCEPTANCE =
            Map.of("privacy-2026-11.1", "privacy-2026-11", "privacy-2026-12.1", "privacy-2026-12");

    /** The documents a person accepts, in the order they are listed. */
    static final List<ConsentPurpose> LISTED = List.of(ConsentPurpose.TERMS, ConsentPurpose.PRIVACY);

    private LegalDocuments() {}

    /** The versions in force, given whether billing is on. */
    static Map<ConsentPurpose, String> current(boolean billingEnabled) {
        return billingEnabled ? WITH_BILLING : WITHOUT_BILLING;
    }
}
