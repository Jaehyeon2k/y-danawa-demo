package yju.danawa.com.service;

import yju.danawa.com.domain.User;
import yju.danawa.com.domain.UserRole;
import yju.danawa.com.dto.UserProfileDto;
import yju.danawa.com.repository.UserRoleRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserProfileService {

    private final UserService userService;
    private final UserRoleRepository userRoleRepository;

    public UserProfileService(UserService userService, UserRoleRepository userRoleRepository) {
        this.userService = userService;
        this.userRoleRepository = userRoleRepository;
    }

    public List<String> getRoleNames(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<UserRole> roles = userRoleRepository.findByUserUserId(userId);
        return roles.stream()
                .map(UserRole::getRoleName)
                .sorted()
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = "users", key = "'profile:' + #username.toLowerCase()")
    public UserProfileDto getProfile(String username) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<String> roleNames = getRoleNames(user.getUserId());

        return new UserProfileDto(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getDepartment(),
                user.getStudentId(),
                user.getPhone(),
                user.getIsEnabled(),
                user.getIsLocked(),
                user.getFailedLoginAttempts(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                roleNames
        );
    }
}
