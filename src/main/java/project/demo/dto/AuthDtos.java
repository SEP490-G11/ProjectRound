package project.demo.dto;

import jakarta.validation.constraints.*;
import project.demo.enums.Role;

public class AuthDtos {

    // Register step 1: send OTP (store draft in session)
    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 64) String password,
            @NotBlank @Size(max = 120) String fullName
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record UserResponse(
            Long id,
            String email,
            String fullName,
            Role role,
            boolean emailVerified,
            boolean isActive
    ) {}

    public record LoginResponse(
            String accessToken,
            UserResponse user
    ) {}

    public record RefreshResponse(String accessToken) {}

    public record MessageResponse(String message) {}

    // OTP verify (register/forgot)
    public record VerifyOtpRequest(
            @NotBlank
            @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be 6 digits")
            String otp
    ) {}

    // Forgot step 1: request OTP
    public record ForgotRequest(
            @Email @NotBlank String email
    ) {}

    // Forgot step 3: reset password after OTP verified (session)
    public record ResetPasswordRequest(
            @NotBlank @Size(min = 8, max = 64) String newPassword,
            @NotBlank @Size(min = 8, max = 64) String confirmNewPassword
    ) {}

    // Logged-in change password
    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 64) String newPassword,
            @NotBlank @Size(min = 8, max = 64) String confirmNewPassword
    ) {}
}
