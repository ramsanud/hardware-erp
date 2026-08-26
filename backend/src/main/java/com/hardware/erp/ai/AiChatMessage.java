package com.hardware.erp.ai;

/** One prior turn in the conversation, as shown to the user - role is "user" or "assistant". */
public record AiChatMessage(String role, String content) {
}
