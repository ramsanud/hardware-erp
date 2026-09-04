package com.hardware.erp.notification.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.common.activity.ActivityAction;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.notification.dto.WhatsAppConnectionRequest;
import com.hardware.erp.notification.dto.WhatsAppConnectionResponse;
import com.hardware.erp.notification.entity.TenantWhatsAppConnection;
import com.hardware.erp.notification.entity.WhatsAppConnectionStatus;
import com.hardware.erp.notification.repository.TenantWhatsAppConnectionRepository;
import com.hardware.erp.notification.service.TenantWhatsAppConnectionService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * CR-056. connect() makes a real, live GET call to Meta's Graph API using
 * the credentials the owner just pasted in, before anything is written to
 * tenant_whatsapp_connection - a phone number id or token typo is caught
 * immediately as "could not verify", not discovered later on the first
 * real invoice send. Nothing here ever returns the access token back to
 * the caller; see WhatsAppConnectionResponse's own shape.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantWhatsAppConnectionServiceImpl implements TenantWhatsAppConnectionService {

    private static final String MODULE = "WHATSAPP";
    private static final String ENTITY = "WHATSAPP_CONNECTION";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final TenantWhatsAppConnectionRepository connectionRepository;
    private final TenantRepository tenantRepository;
    private final WhatsAppProperties properties;
    private final ObjectMapper objectMapper;
    private final ActivityLogService activityLog;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Override
    @Transactional(readOnly = true)
    public WhatsAppConnectionResponse getStatus() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return connectionRepository.findByTenantId(tenantId)
                .map(this::toResponse)
                .orElseGet(WhatsAppConnectionResponse::notConnected);
    }

    @Override
    @Transactional
    public WhatsAppConnectionResponse connect(WhatsAppConnectionRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        TenantWhatsAppConnection connection = connectionRepository.findByTenantId(tenantId).orElse(null);
        boolean isNewConnection = connection == null;
        boolean phoneNumberChanged = isNewConnection || !connection.getPhoneNumberId().equals(request.phoneNumberId());

        // Checked before ever calling Meta - a phone number already connected
        // to a different tenant is rejected immediately, both so one tenant
        // can never even attempt to claim another's number (spec §4/§6) and
        // so a doomed request never burns a real Graph API call first.
        if (phoneNumberChanged) {
            connectionRepository.findByPhoneNumberId(request.phoneNumberId()).ifPresent(other -> {
                throw new DuplicateResourceException("WhatsApp phone number", request.phoneNumberId());
            });
        }

        VerifiedPhone verified = verifyWithMeta(request.phoneNumberId(), request.accessToken());

        if (isNewConnection) {
            connection = TenantWhatsAppConnection.builder()
                    .tenant(tenantRepository.getReferenceById(tenantId))
                    .connectedAt(LocalDateTime.now())
                    .build();
        } else if (phoneNumberChanged) {
            connection.setConnectedAt(LocalDateTime.now());
        }

        connection.setBusinessAccountId(request.businessAccountId());
        connection.setPhoneNumberId(request.phoneNumberId());
        connection.setDisplayPhoneNumber(verified.displayPhoneNumber());
        connection.setBusinessName(verified.verifiedName());
        connection.setAccessToken(request.accessToken());
        connection.setConnectionStatus(WhatsAppConnectionStatus.CONNECTED);
        connection.setLastVerifiedAt(LocalDateTime.now());
        connection.setDisconnectedAt(null);

        TenantWhatsAppConnection saved = connectionRepository.save(connection);
        // Never log the access token - only the identifiers a support/audit review needs.
        activityLog.action(MODULE, ENTITY, saved.getId(), saved.getBusinessName(),
                isNewConnection ? ActivityAction.CREATE : ActivityAction.UPDATE,
                "WhatsApp Business connected: " + saved.getDisplayPhoneNumber());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public WhatsAppConnectionResponse disconnect() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        TenantWhatsAppConnection connection = connectionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("WhatsApp connection"));

        connection.setConnectionStatus(WhatsAppConnectionStatus.DISCONNECTED);
        connection.setAccessToken(null);
        connection.setDisconnectedAt(LocalDateTime.now());
        TenantWhatsAppConnection saved = connectionRepository.save(connection);

        activityLog.action(MODULE, ENTITY, saved.getId(), saved.getBusinessName(),
                ActivityAction.STATUS_CHANGE, "WhatsApp Business disconnected");
        return toResponse(saved);
    }

    // ---------------------------------------------------------------

    private VerifiedPhone verifyWithMeta(String phoneNumberId, String accessToken) {
        try {
            String url = properties.apiBaseUrl() + "/" + phoneNumberId + "?fields=verified_name,display_phone_number";
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());

            if (response.statusCode() / 100 != 2) {
                String metaMessage = root.path("error").path("message").asText(null);
                log.warn("Meta WhatsApp verification failed for phone number id {}: HTTP {} - {}",
                        phoneNumberId, response.statusCode(), metaMessage);
                throw new BusinessException(
                        "Could not verify this WhatsApp Business phone number. Check the access token and "
                        + "phone number ID and try again."
                        + (metaMessage != null ? " (Meta said: " + metaMessage + ")" : ""));
            }

            String displayPhoneNumber = root.path("display_phone_number").asText(null);
            String verifiedName = root.path("verified_name").asText(null);
            if (displayPhoneNumber == null) {
                throw new BusinessException("Meta did not return a phone number for this connection. "
                        + "Check the phone number ID and try again.");
            }
            return new VerifiedPhone(displayPhoneNumber, verifiedName != null ? verifiedName : "WhatsApp Business");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Meta WhatsApp verification call failed for phone number id {}", phoneNumberId, ex);
            throw new BusinessException("Could not reach WhatsApp right now. Please try again shortly.");
        }
    }

    private WhatsAppConnectionResponse toResponse(TenantWhatsAppConnection connection) {
        return new WhatsAppConnectionResponse(
                connection.isConnected(),
                connection.getConnectionStatus(),
                connection.getBusinessName(),
                connection.maskedPhoneNumber(),
                connection.getConnectedAt(),
                connection.getLastVerifiedAt());
    }

    private record VerifiedPhone(String displayPhoneNumber, String verifiedName) {}
}
