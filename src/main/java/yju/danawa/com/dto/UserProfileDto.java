package yju.danawa.com.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserProfileDto(
        Long userId,
        String username,
        String email,
        String fullName,
        String department,
        String studentId,
        String phone,
        Boolean isEnabled,
        Boolean isLocked,
        Integer failedLoginAttempts,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> roles
) {
}
