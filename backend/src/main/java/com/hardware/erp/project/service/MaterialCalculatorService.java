package com.hardware.erp.project.service;

import com.hardware.erp.project.dto.RooftopCalculatorRequest;
import com.hardware.erp.project.dto.RooftopCalculatorResponse;

public interface MaterialCalculatorService {

    RooftopCalculatorResponse rooftopSheets(RooftopCalculatorRequest request);
}
