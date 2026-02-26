package yju.danawa.com.web;

import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank String username,
        @NotBlank String password,
        String email,
        String fullName,
        String department,
        @NotBlank String studentId,
        String phone
) {
}
