package com.hardware.erp.auth.service.impl;

import com.hardware.erp.auth.entity.UserAvatar;
import com.hardware.erp.auth.repository.UserAvatarRepository;
import com.hardware.erp.auth.service.AvatarService;
import com.hardware.erp.common.image.ImageValidation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AvatarServiceImpl implements AvatarService {

    private final UserAvatarRepository avatarRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAvatar> get(Long userId) {
        return avatarRepository.findById(userId);
    }

    @Override
    @Transactional
    public void upload(Long userId, MultipartFile file) {
        ImageValidation.validate(file, ImageValidation.PHOTO_TYPES);
        try {
            UserAvatar avatar = avatarRepository.findById(userId)
                    .orElse(UserAvatar.builder().userId(userId).build());
            avatar.setContentType(file.getContentType());
            avatar.setFileSize((int) file.getSize());
            avatar.setImageData(file.getBytes());
            avatar.setUpdatedAt(LocalDateTime.now());
            avatarRepository.save(avatar);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded image", e);
        }
    }

    @Override
    @Transactional
    public void remove(Long userId) {
        avatarRepository.deleteById(userId);
    }
}
