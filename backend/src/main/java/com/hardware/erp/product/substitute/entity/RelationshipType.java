package com.hardware.erp.product.substitute.entity;

/**
 * CR-089 §17. A relationship the owner defined by hand. Manual mappings
 * always outrank the rule-based similarity score - for safety-sensitive
 * goods (electrical, plumbing fittings, load-bearing hardware, locks) an
 * explicit mapping is the ONLY thing that may drive a suggestion.
 */
public enum RelationshipType {
    /** A different product that does the same job. */
    ALTERNATIVE,
    /** Works together with, or fits, the source product. */
    COMPATIBLE,
    /** A better/heavier-duty version of the same thing. */
    UPGRADE,
    /** A cheaper version of the same thing. */
    LOWER_COST,
    /** Same use, different construction. */
    SAME_USE,
    /** A direct replacement, e.g. a discontinued line's successor. */
    REPLACEMENT;

    /**
     * Whether this relationship answers "what else can I sell this customer
     * instead?". COMPATIBLE is deliberately excluded: a compatible product
     * goes WITH the requested one (a matching hinge for a door), it is not a
     * substitute FOR it, and offering it as one would be exactly the
     * "unsafe/incorrect substitution" §16 warns about.
     */
    public boolean isSubstitute() {
        return this != COMPATIBLE;
    }
}
