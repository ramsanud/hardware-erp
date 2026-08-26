package com.hardware.erp.project.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.project.dto.RooftopCalculatorRequest;
import com.hardware.erp.project.dto.RooftopCalculatorResponse;
import com.hardware.erp.project.service.MaterialCalculatorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stateless estimators - nothing here is persisted; the user adds an actual project_material line afterward with their own chosen quantity. */
@RestController
@RequestMapping("/v1/projects/calculators")
@RequiredArgsConstructor
@Tag(name = "13. Projects")
public class MaterialCalculatorController {

    private final MaterialCalculatorService calculatorService;

    @PostMapping("/rooftop-sheet")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).PROJECT_MATERIAL_VIEW)")
    public ApiResponse<RooftopCalculatorResponse> rooftopSheets(@Valid @RequestBody RooftopCalculatorRequest request) {
        return ApiResponse.ok(calculatorService.rooftopSheets(request));
    }
}
