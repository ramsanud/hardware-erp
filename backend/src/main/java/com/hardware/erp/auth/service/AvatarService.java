package com.hardware.erp.auth.service;

import com.hardware.erp.auth.entity.UserAvatar;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

public interface AvatarService {

    Optional<UserAvatar> get(Long userId);

    void upload(Long userId, MultipartFile file);

    void remove(Long userId);
}
