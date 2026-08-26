package com.hardware.erp.legal.entity;

public enum ConsentType {
    /** Agreement to the Terms & Conditions. Required to hold an account. */
    TERMS,
    /** Acknowledgement of the Privacy Policy. Required to hold an account. */
    PRIVACY,
    /** Optional, revocable preference for promotional email. Never required. */
    MARKETING
}
