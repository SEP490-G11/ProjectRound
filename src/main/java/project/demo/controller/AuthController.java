package project.demo.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import project.demo.dto.AuthDtos;
import project.demo.entity.User;
import project.demo.security.CustomUserDetails;
import project.demo.service.AuthService;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.refresh.cookie-name}")
    private String cookieName;
    @Value("${app.refresh.cookie-path}")
    private String cookiePath;
    @Value("${app.refresh.secure}")
    private boolean cookieSecure;
    @Value("${app.refresh.same-site}")
    private String sameSite;
    @Value("${app.refresh.days}")
    private int refreshDays;

    private HttpSession session(HttpServletRequest req) {
        return req.getSession(true);
    }

    // =========================
    // REGISTER OTP
    // =========================

    @PostMapping("/register/request-otp")
    public ResponseEntity<AuthDtos.MessageResponse> registerRequestOtp(
            @Valid @RequestBody AuthDtos.RegisterRequest req,
            HttpServletRequest httpReq
    ) {
        authService.registerRequestOtp(req, session(httpReq));
        return ResponseEntity.ok(new AuthDtos.MessageResponse("OTP_SENT"));
    }

    @PostMapping("/register/verify-otp")
    public ResponseEntity<AuthDtos.MessageResponse> registerVerifyOtp(
            @Valid @RequestBody AuthDtos.VerifyOtpRequest req,
            HttpServletRequest httpReq
    ) {
        authService.registerVerifyOtp(req.otp(), session(httpReq));
        return ResponseEntity.ok(new AuthDtos.MessageResponse("REGISTERED_SUCCESS"));
    }

    // =========================
    // FORGOT PASSWORD OTP
    // =========================

    @PostMapping("/forgot/request-otp")
    public ResponseEntity<AuthDtos.MessageResponse> forgotRequestOtp(
            @Valid @RequestBody AuthDtos.ForgotRequest req,
            HttpServletRequest httpReq
    ) {
        authService.forgotRequestOtp(req, session(httpReq));
        return ResponseEntity.ok(new AuthDtos.MessageResponse("OTP_SENT"));
    }

    @PostMapping("/forgot/verify-otp")
    public ResponseEntity<AuthDtos.MessageResponse> forgotVerifyOtp(
            @Valid @RequestBody AuthDtos.VerifyOtpRequest req,
            HttpServletRequest httpReq
    ) {
        authService.forgotVerifyOtp(req.otp(), session(httpReq));
        return ResponseEntity.ok(new AuthDtos.MessageResponse("OTP_VERIFIED"));
    }

    @PostMapping("/forgot/reset-password")
    public ResponseEntity<AuthDtos.MessageResponse> forgotResetPassword(
            @Valid @RequestBody AuthDtos.ResetPasswordRequest req,
            HttpServletRequest httpReq
    ) {
        authService.forgotResetPassword(req, session(httpReq));
        return ResponseEntity.ok(new AuthDtos.MessageResponse("PASSWORD_RESET_SUCCESS"));
    }

    // =========================
    // CHANGE PASSWORD (LOGGED-IN)
    // =========================

    @PatchMapping("/change-password")
    public ResponseEntity<AuthDtos.MessageResponse> changePassword(
            @Valid @RequestBody AuthDtos.ChangePasswordRequest req,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        if (principal == null) throw new RuntimeException("UNAUTHORIZED");
        authService.changePassword(principal.getId(), req);
        return ResponseEntity.ok(new AuthDtos.MessageResponse("PASSWORD_CHANGED"));
    }

    // =========================
    // LOGIN / REFRESH / LOGOUT (GIỮ NGUYÊN)
    // =========================

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest req,
                                                        HttpServletRequest httpReq) {
        var result = authService.login(req, httpReq);
        ResponseCookie refreshCookie = buildRefreshCookie(result.rawRefreshToken());

        User u = result.user();
        var userRes = new AuthDtos.UserResponse(
                u.getId(), u.getEmail(), u.getFullName(),
                u.getRole(), u.isEmailVerified(), u.isActive()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new AuthDtos.LoginResponse(result.accessToken(), userRes));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthDtos.RefreshResponse> refresh(HttpServletRequest httpReq) {
        String rawRefresh = getCookieValue(httpReq, cookieName);

        var result = authService.refresh(rawRefresh, httpReq);
        ResponseCookie newCookie = buildRefreshCookie(result.rawRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, newCookie.toString())
                .body(new AuthDtos.RefreshResponse(result.accessToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthDtos.MessageResponse> logout(HttpServletRequest httpReq) {
        String rawRefresh = getCookieValue(httpReq, cookieName);

        authService.logout(rawRefresh);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .body(new AuthDtos.MessageResponse("LOGGED_OUT"));
    }

    // =========================
    // COOKIE HELPERS
    // =========================

    private String getCookieValue(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private ResponseCookie buildRefreshCookie(String rawRefresh) {
        return ResponseCookie.from(cookieName, rawRefresh)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .path(cookiePath)
                .maxAge(Duration.ofDays(refreshDays))
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .path(cookiePath)
                .maxAge(Duration.ZERO)
                .build();
    }
}
