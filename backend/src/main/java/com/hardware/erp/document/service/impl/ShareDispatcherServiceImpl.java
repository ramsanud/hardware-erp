package com.hardware.erp.document.service.impl;

import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.customer.dto.CustomerResponse;
import com.hardware.erp.customer.service.CustomerService;
import com.hardware.erp.document.service.ReportJobService;
import com.hardware.erp.document.service.ReportJobService.DownloadableFile;
import com.hardware.erp.document.service.ShareDispatcherService;
import com.hardware.erp.notification.dto.WhatsAppLinkResponse;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.service.EmailTransport;
import com.hardware.erp.notification.service.NotificationAttachment;
import com.hardware.erp.notification.whatsapp.WhatsAppMessageTemplates;
import com.hardware.erp.notification.whatsapp.WhatsAppService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * CR-101. Tenant isolation is inherited, not re-implemented, the same way
 * WhatsAppLinkService documents it: every lookup goes through
 * {@link ReportJobService#download} or {@link CustomerService#get}, each of
 * which already resolves the caller's tenant and answers 404/not-found for
 * anyone else's row - there is no repository access in this class.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareDispatcherServiceImpl implements ShareDispatcherService {

    private final ReportJobService reportJobService;
    private final CustomerService customerService;
    private final TenantRepository tenantRepository;
    private final WhatsAppService whatsAppService;
    private final WhatsAppMessageTemplates templates;
    private final EmailTransport emailTransport;

    @Override
    public WhatsAppLinkResponse whatsAppLinkForJob(Long jobId, String toMobileNo) {
        DownloadableFile file = reportJobService.download(jobId);
        String message = templates.document(shopName(), file.fileName(),
                "The file (" + file.fileName() + ") is being sent separately - please look out for it.");
        // A report has no single recipient the way an invoice has its
        // customer, so a blank number opens WhatsApp's own contact chooser
        // (generateChatUrl would refuse a blank number outright - it exists
        // for a *known* customer, which this call has none of by default).
        boolean hasRecipient = toMobileNo != null && !toMobileNo.isBlank();
        String url = hasRecipient
                ? whatsAppService.generateChatUrl(toMobileNo, message)
                : whatsAppService.generateChooserUrl(message);
        return new WhatsAppLinkResponse(url, toMobileNo, message);
    }

    @Override
    public NotificationStatus emailJob(Long jobId, String toEmail) {
        DownloadableFile file = reportJobService.download(jobId);
        String shopName = shopName();
        try {
            return emailTransport.sendEmail(toEmail, file.fileName() + " from " + shopName,
                    """
                    Hello,

                    Please find attached %s.

                    Thank you,
                    %s
                    """.formatted(file.fileName(), shopName),
                    new NotificationAttachment(file.fileName(), file.contentType(), file.bytes()))
                    .status();
        } catch (Exception e) {
            log.error("Failed to email report job {}", jobId, e);
            return NotificationStatus.FAILED;
        }
    }

    @Override
    public WhatsAppLinkResponse occasionGreetingLink(Long customerId, String occasionLabel) {
        if (occasionLabel == null || occasionLabel.isBlank()) {
            throw new BusinessException("occasionLabel is required");
        }
        CustomerResponse customer = customerService.get(customerId);
        if (customer.mobileNo() == null || customer.mobileNo().isBlank()) {
            throw new BusinessException("This customer does not have a phone number.");
        }
        String message = templates.occasionGreeting(shopName(), customer.customerName(), occasionLabel);
        return new WhatsAppLinkResponse(
                whatsAppService.generateChatUrl(customer.mobileNo(), message), customer.mobileNo(), message);
    }

    private String shopName() {
        return tenantRepository.findById(SecurityUtils.requireCurrentTenantId())
                .map(Tenant::getName).orElse("");
    }
}
