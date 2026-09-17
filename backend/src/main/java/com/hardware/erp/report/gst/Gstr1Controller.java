package com.hardware.erp.report.gst;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * CR-087. GSTR-1 is a statutory filing, so it sits behind REPORT_FINANCIAL
 * with the Tally export, not behind the operational REPORT_VIEW.
 */
@RestController
@RequestMapping("/v1/reports/gstr1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reports")
@PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).REPORT_FINANCIAL)")
public class Gstr1Controller {

    private final Gstr1Service gstr1Service;
    private final ObjectMapper objectMapper;

    @GetMapping
    @Operation(summary = "GSTR-1 for a return period, in the offline tool's JSON layout (enveloped, for preview)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> preview(
            @Parameter(example = "092026", description = "MMYYYY") @RequestParam String period) {
        return ResponseEntity.ok(ApiResponse.ok(gstr1Service.build(period)));
    }

    @GetMapping("/download")
    @Operation(summary = "The same GSTR-1 as a bare JSON file the GST offline tool can import")
    public ResponseEntity<byte[]> download(@RequestParam String period) {
        try {
            byte[] body = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(gstr1Service.build(period)).getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Content-Disposition", "attachment; filename=\"GSTR1-" + period + ".json\"")
                    .body(body);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
