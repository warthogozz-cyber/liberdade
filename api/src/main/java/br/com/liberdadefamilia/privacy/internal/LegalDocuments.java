package br.com.liberdadefamilia.privacy.internal;

import java.util.List;
import java.util.Map;

/** The version of each legal document in force. Publishing a new text means bumping its version here. */
final class LegalDocuments {

    static final Map<ConsentPurpose, String> CURRENT =
            Map.of(ConsentPurpose.TERMS, "terms-2026-11", ConsentPurpose.PRIVACY, "privacy-2026-11");

    /** The documents a person accepts, in the order they are listed. */
    static final List<ConsentPurpose> LISTED = List.of(ConsentPurpose.TERMS, ConsentPurpose.PRIVACY);

    private LegalDocuments() {}
}
