package com.hardware.erp.ai;

import com.hardware.erp.ai.tool.AiTool;
import com.hardware.erp.ai.tool.AiToolRegistry;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.hardware.erp.tenant.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read-only, permission-aware chat over the caller's own shop data (CR-027).
 * Never generates SQL and never sees another tenant's data - every answer
 * comes from a small, fixed set of AiTool calls, each already scoped by the
 * existing service layer (SecurityUtils.requireCurrentTenantId() inside
 * each), and each tool is only offered to the model if the caller holds its
 * required permission.
 */
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final ChatCompletionClient chatCompletionClient;
    private final AiToolRegistry toolRegistry;
    private final SubscriptionService subscriptionService;
    private final TenantRepository tenantRepository;

    public String reply(List<AiChatMessage> history, String message) {
        subscriptionService.requireTier(SubscriptionTier.MAX);

        if (!chatCompletionClient.isConfigured()) {
            return "The AI assistant isn't set up yet - ask whoever manages this system to add an AI provider API key.";
        }

        AppUserDetails user = SecurityUtils.requireCurrentUser();
        List<AiTool> availableTools = toolRegistry.availableTo(user::hasPermission);

        String shopName = tenantRepository.findById(user.getTenantId())
                .map(Tenant::getName).orElse("this shop");

        String systemPrompt = """
                You are the AI assistant built into a hardware shop's ERP system, answering \
                questions for the staff of "%s".

                Rules:
                - Answer only questions about this shop's own data - customers, invoices, \
                  quotations, stock, and sales. Use the tools you're given to look up real \
                  numbers; never guess or make up a figure.
                - If a question needs a tool you were not given, say plainly that you don't \
                  have access to that information, rather than answering from general \
                  knowledge or the tools you weren't offered.
                - If nothing you were asked needs a lookup (e.g. "hello", "what can you do"), \
                  answer directly without calling a tool.
                - Keep answers short and to the point - this is read on a small chat panel, \
                  not a report.
                - Never invent GST, legal, or accounting advice you are not certain of; suggest \
                  the shop owner confirm with their accountant for anything beyond simple facts \
                  already in the data.
                """.formatted(shopName);

        return chatCompletionClient.chat(systemPrompt, history, message, availableTools);
    }
}
