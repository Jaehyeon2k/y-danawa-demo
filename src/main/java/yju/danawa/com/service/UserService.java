package yju.danawa.com.service;

import yju.danawa.com.domain.User;
import yju.danawa.com.repository.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Cacheable(cacheNames = "users", key = "#username == null ? '' : #username.toLowerCase()")
    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByUsernameIgnoreCase(username.trim());
    }

    @CacheEvict(cacheNames = "users", key = "#user.username == null ? '' : #user.username.toLowerCase()")
    public User save(User user) {
        return userRepository.save(user);
    }

    public boolean existsByUsername(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return userRepository.existsByUsernameIgnoreCase(username.trim());
    }

    public boolean existsByStudentId(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            return false;
        }
        return userRepository.existsByStudentId(studentId.trim());
    }

    public boolean isPasswordInUse(String rawPassword, PasswordEncoder passwordEncoder) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return false;
        }
        return userRepository.findAllPasswordHashes().stream()
                .anyMatch(hash -> passwordEncoder.matches(rawPassword, hash));
    }
}
