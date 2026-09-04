package com.hardware.erp.config;

import com.hardware.erp.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CR-059 - tells the browser which deployment it is talking to, so the UI can
 * stop offering things this installation does not have (a plan upgrade on a
 * self-hosted licence, most of all).
 *
 * Public and unauthenticated, for the same reason /v1/auth/captcha-config is:
 * the shell renders before anyone has signed in, and a self-hosted install
 * should not flash a billing prompt while the session is still loading.
 *
 * Everything here is already visible to anyone who can reach the login page -
 * the mode, and whether billing exists. No host, credential, provider key or
 * version string is served: those would be reconnaissance with no benefit to
 * a shop owner (the same reasoning that turns springdoc off in production).
 */
@RestController
@RequestMapping("/v1/deployment-config")
@RequiredArgsConstructor
@Tag(name = "Deployment", description = "Which installation this is - hosted SaaS or self-hosted")
public class DeploymentConfigController {

    private final DeploymentProperties deployment;

    @GetMapping
    @Operation(
            summary = "Deployment mode and the features that follow from it",
            description = """
                    Public and unauthenticated - the app shell needs this before
                    sign-in.

                    `selfHosted` is true for an on-premise Docker install.
                    `billingEnabled` is false there by default: the client has
                    already bought the software, so subscription checkout and
                    plan-upgrade prompts are hidden and the checkout endpoint
                    answers 503 BILLING_NOT_APPLICABLE.""")
    public ApiResponse<DeploymentConfigResponse> deploymentConfig() {
        return ApiResponse.ok(new DeploymentConfigResponse(
                deployment.mode(),
                deployment.selfHosted(),
                deployment.billingApplies(),
                deployment.installationName()));
    }
}
