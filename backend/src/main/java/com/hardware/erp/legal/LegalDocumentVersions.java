package com.hardware.erp.legal;

import com.hardware.erp.legal.entity.ConsentType;

/**
 * The single source of truth for which legal document version is current.
 *
 * The server validates the version a client submits against these constants
 * rather than storing whatever string it is handed. Without that check a
 * client could record acceptance of a version that was never published - or
 * of an old one it preferred - and the consent record would be worthless as
 * evidence.
 *
 * Bump the constant AND publish the matching copy in the frontend's
 * LegalContent.tsx in the same change. A version here with no document behind
 * it is worse than no version at all.
 *
 * LEGAL REVIEW REQUIRED before treating any record keyed on these as proof of
 * consent in a given jurisdiction.
 */
public final class LegalDocumentVersions {

    private LegalDocumentVersions() {
    }

    public static final String TERMS_VERSION = "1.0";
    public static final String PRIVACY_VERSION = "1.0";

    public static String currentFor(ConsentType type) {
        return switch (type) {
            case TERMS -> TERMS_VERSION;
            case PRIVACY -> PRIVACY_VERSION;
            // A preference, not agreement to a published document.
            case MARKETING -> null;
        };
    }
}
