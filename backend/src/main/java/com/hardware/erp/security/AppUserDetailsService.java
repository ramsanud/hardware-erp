package com.hardware.erp.security;

import com.hardware.erp.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Optional<AppUserDetails> loadById(Long userId) {
        return userRepository.findById(userId).map(AppUserDetails::new);
    }

    @Transactional(readOnly = true)
    public AppUserDetails requireById(Long userId) {
        return loadById(userId).orElseThrow(
                () -> new UsernameNotFoundException("No user with id " + userId));
    }
}
