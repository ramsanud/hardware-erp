package com.hardware.erp.product.extraction;

import java.util.List;

public record ProductExtractionResult(List<ExtractedProductRow> rows, List<String> warnings) {}
