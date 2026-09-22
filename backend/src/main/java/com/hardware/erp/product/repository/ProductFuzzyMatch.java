package com.hardware.erp.product.repository;

/**
 * CR-097. One row of {@link ProductRepository#fuzzySearch}: the product's id
 * and how closely it matched, nothing else. The entity is loaded separately
 * so the summary DTO is built by the same mapper as the exact-match path -
 * a second projection of the product would be a second place to forget that
 * the list never carries purchase price.
 */
public interface ProductFuzzyMatch {

    Long getProductId();

    /** word_similarity of the query against the name or code, whichever is higher; 0.0-1.0. */
    Double getScore();
}
