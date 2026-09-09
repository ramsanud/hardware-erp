package com.hardware.erp.tenant.dto;

import java.util.List;

/**
 * What the reset actually removed (CR-067) - real row counts from the delete
 * statements, not the preview's estimate re-displayed. The two can legitimately
 * differ if someone raised an invoice between the preview and the confirmation.
 */
public record DataResetResponse(
        List<DataResetPreviewResponse.Group> groups,
        long totalRecords
) {}
