package com.hardware.erp.platformadmin.security;

import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlatformAdminUserDetailsService {

    private final PlatformAdminRepository platformAdminRepository;

    @Transactional(readOnly = true)
    public Optional<PlatformAdminPrincipal> loadById(Long adminId) {
        return platformAdminRepository.findById(adminId).map(PlatformAdminPrincipal::new);
    }

    @Transactional(readOnly = true)
    public PlatformAdminPrincipal requireById(Long adminId) {
        return loadById(adminId).orElseThrow(
                () -> new UsernameNotFoundException("No platform admin with id " + adminId));
    }
}
