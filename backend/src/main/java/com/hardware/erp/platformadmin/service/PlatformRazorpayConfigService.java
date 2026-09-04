package com.hardware.erp.platformadmin.service;

import com.hardware.erp.platformadmin.dto.RazorpayConfigResponse;
import com.hardware.erp.platformadmin.dto.UpdateRazorpayConfigRequest;
import jakarta.servlet.http.HttpServletRequest;

public interface PlatformRazorpayConfigService {

    RazorpayConfigResponse get();

    RazorpayConfigResponse update(UpdateRazorpayConfigRequest request, Long adminId, HttpServletRequest httpRequest);
}
