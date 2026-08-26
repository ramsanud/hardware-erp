package com.hardware.erp.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** history is client-held, stateless across requests - no conversation is persisted server-side (CR-027). */
public record AiChatRequest(
        @NotBlank @Size(max = 2000) String message,
        List<AiChatMessage> history
) {}
