package com.hardware.erp.subscription.exception;

import com.hardware.erp.common.exception.BusinessException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * CR-088. 403 with a structured body the frontend turns into the upgrade
 * dialog: which feature, the plan the shop is on, and the cheapest plan
 * that carries the feature. Never blocks anything but the one call.
 */
@Getter
public class FeatureNotAvailableException extends BusinessException {

    public static final String CODE = "FEATURE_NOT_AVAILABLE";

    private final String featureKey;
    private final String featureName;
    private final String currentPlanCode;
    private final String currentPlanName;
    private final String requiredPlanCode;
    private final String requiredPlanName;

    public FeatureNotAvailableException(String featureKey, String featureName,
                                        String currentPlanCode, String currentPlanName,
                                        String requiredPlanCode, String requiredPlanName) {
        super("\"" + featureName + "\" is available in the " + requiredPlanName + " plan. Your shop is on "
                + currentPlanName + ".", HttpStatus.FORBIDDEN, CODE);
        this.featureKey = featureKey;
        this.featureName = featureName;
        this.currentPlanCode = currentPlanCode;
        this.currentPlanName = currentPlanName;
        this.requiredPlanCode = requiredPlanCode;
        this.requiredPlanName = requiredPlanName;
    }
}
