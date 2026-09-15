package com.hardware.erp.notification.service.impl;

import com.hardware.erp.notification.entity.NotificationChannel;
import com.hardware.erp.notification.entity.NotificationLog;
import com.hardware.erp.notification.entity.NotificationStatus;
import com.hardware.erp.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CR-085. The forward-only rule that used to live inline in the Meta
 * webhook - and had no test there - now serves three providers, so it is
 * pinned here once.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryStatusServiceImplTest {

    @Mock private NotificationLogRepository repository;
    @InjectMocks private DeliveryStatusServiceImpl service;

    private NotificationLog rowAt(NotificationStatus status) {
        NotificationLog row = new NotificationLog();
        row.setStatus(status);
        return row;
    }

    @Test
    @DisplayName("SENT advances to DELIVERED, DELIVERED to READ, and a late DELIVERED never regresses READ")
    void forwardOnly() {
        assertThat(DeliveryStatusServiceImpl.isForwardProgress(NotificationStatus.SENT, NotificationStatus.DELIVERED)).isTrue();
        assertThat(DeliveryStatusServiceImpl.isForwardProgress(NotificationStatus.DELIVERED, NotificationStatus.READ)).isTrue();
        assertThat(DeliveryStatusServiceImpl.isForwardProgress(NotificationStatus.READ, NotificationStatus.DELIVERED)).isFalse();
        assertThat(DeliveryStatusServiceImpl.isForwardProgress(NotificationStatus.DELIVERED, NotificationStatus.DELIVERED)).isFalse();
    }

    @Test
    @DisplayName("FAILED is believed only for a message that was merely SENT - a delivered message did not fail")
    void failedOnlyFromSent() {
        assertThat(DeliveryStatusServiceImpl.isForwardProgress(NotificationStatus.SENT, NotificationStatus.FAILED)).isTrue();
        assertThat(DeliveryStatusServiceImpl.isForwardProgress(NotificationStatus.DELIVERED, NotificationStatus.FAILED)).isFalse();
        assertThat(DeliveryStatusServiceImpl.isForwardProgress(NotificationStatus.LOGGED_ONLY, NotificationStatus.FAILED)).isFalse();
    }

    @Test
    @DisplayName("a channel-scoped callback finds the row by channel and provider id and saves the advanced status")
    void channelScopedApplySaves() {
        NotificationLog row = rowAt(NotificationStatus.SENT);
        when(repository.findByChannelAndProviderMessageId(NotificationChannel.SMS, "SM123")).thenReturn(Optional.of(row));

        service.apply(NotificationChannel.SMS, "SM123", NotificationStatus.DELIVERED);

        assertThat(row.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
        verify(repository).save(row);
    }

    @Test
    @DisplayName("an unknown id, a null status, or a non-advancing status writes nothing")
    void noWriteWhenNothingToDo() {
        when(repository.findByChannelAndProviderMessageId(NotificationChannel.EMAIL, "gone")).thenReturn(Optional.empty());
        service.apply(NotificationChannel.EMAIL, "gone", NotificationStatus.DELIVERED);

        service.apply(NotificationChannel.EMAIL, "any", null);

        NotificationLog read = rowAt(NotificationStatus.READ);
        when(repository.findByChannelAndProviderMessageId(NotificationChannel.EMAIL, "read")).thenReturn(Optional.of(read));
        service.apply(NotificationChannel.EMAIL, "read", NotificationStatus.DELIVERED);

        verify(repository, never()).save(any());
        assertThat(read.getStatus()).isEqualTo(NotificationStatus.READ);
    }
}
