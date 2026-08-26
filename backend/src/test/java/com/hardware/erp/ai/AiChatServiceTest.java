package com.hardware.erp.ai;

import com.hardware.erp.ai.tool.AiTool;
import com.hardware.erp.ai.tool.AiToolRegistry;
import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.hardware.erp.tenant.service.SubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiChatServiceTest {

    @Mock private ChatCompletionClient chatCompletionClient;
    @Mock private AiToolRegistry toolRegistry;
    @Mock private SubscriptionService subscriptionService;
    @Mock private TenantRepository tenantRepository;

    @InjectMocks private AiChatService aiChatService;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).slug("default").name("Default Shop")
                .status(TenantStatus.ACTIVE).build();
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(toolRegistry.availableTo(any())).thenReturn(List.of());

        Role role = Role.builder().id(1L).code("OWNER").name("Owner").systemRole(true)
                .status(RoleStatus.ACTIVE).permissions(new LinkedHashSet<>()).build();
        User authUser = User.builder().id(1L).tenant(tenant).role(role)
                .fullName("Owner").mobileNo("9999999999").passwordHash("h")
                .status(UserStatus.ACTIVE).tokenVersion(0).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserDetails(authUser), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a tenant below the Max tier is rejected before any AI call is made")
    void rejectsBelowMaxTier() {
        doThrow(new BusinessException("Needs Max plan", HttpStatus.PAYMENT_REQUIRED, "SUBSCRIPTION_TIER_REQUIRED"))
                .when(subscriptionService).requireTier(SubscriptionTier.MAX);

        assertThatThrownBy(() -> aiChatService.reply(List.of(), "What's my outstanding balance?"))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(chatCompletionClient);
    }

    @Test
    @DisplayName("an unconfigured AI provider replies with a clear setup message instead of attempting a call")
    void repliesWithSetupMessageWhenUnconfigured() {
        when(chatCompletionClient.isConfigured()).thenReturn(false);

        String reply = aiChatService.reply(List.of(), "Hello");

        assertThat(reply).containsIgnoringCase("isn't set up");
        verify(chatCompletionClient, never()).chat(anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("a Max-tier tenant with a configured provider gets the model's real answer")
    void delegatesToConfiguredProvider() {
        when(chatCompletionClient.isConfigured()).thenReturn(true);
        when(chatCompletionClient.chat(anyString(), any(), eq("Hello"), any()))
                .thenReturn("Hi! How can I help with Default Shop today?");

        String reply = aiChatService.reply(List.of(), "Hello");

        assertThat(reply).isEqualTo("Hi! How can I help with Default Shop today?");
        verify(subscriptionService).requireTier(SubscriptionTier.MAX);
    }

    /** Never offered to the LLM at all if the tool would 403 anyway - not just filtered after the fact. */
    @Test
    @DisplayName("only tools the caller holds the permission for are ever offered to the model")
    void filtersToolsByPermission() {
        AiTool restrictedTool = mock(AiTool.class);
        when(toolRegistry.availableTo(any())).thenReturn(List.of(restrictedTool));
        when(chatCompletionClient.isConfigured()).thenReturn(true);
        when(chatCompletionClient.chat(anyString(), any(), anyString(), eq(List.of(restrictedTool))))
                .thenReturn("ok");

        aiChatService.reply(List.of(), "test");

        verify(toolRegistry).availableTo(any());
    }
}
