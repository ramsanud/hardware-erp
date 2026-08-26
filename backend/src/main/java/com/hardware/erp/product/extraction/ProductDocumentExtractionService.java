package com.hardware.erp.product.extraction;

import java.io.InputStream;

/** Pluggable per-format product-file reader (CR-036), mirrors the Purchase module's DocumentExtractionService design. */
public interface ProductDocumentExtractionService {

    boolean supports(String extension);

    ProductExtractionResult extract(InputStream input) throws Exception;
}
