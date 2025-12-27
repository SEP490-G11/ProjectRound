package project.demo.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.demo.dto.AuthDtos;
import project.demo.entity.RefreshToken;
import project.demo.entity.User;
import project.demo.enums.Role;
import project.demo.repository.RefreshTokenRepository;
import project.demo.repository.UserRepository;
import project.demo.security.JwtProvider;
import project.demo.util.TokenUtil;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JavaMailSender mailSender;

    @Value("${app.refresh.days}") private int refreshDays;

    // OTP config
    @Value("${app.otp.minutes:10}") private int otpMinutes;
    @Value("${app.otp.resend-seconds:30}") private int resendSeconds;

    // ===== SESSION KEYS =====
    private static final String REG_DRAFT = "REG_DRAFT";
    private static final String REG_OTP_HASH = "REG_OTP_HASH";
    private static final String REG_OTP_EXP = "REG_OTP_EXP";
    private static final String REG_LAST_SENT = "REG_LAST_SENT";

    private static final String FORGOT_EMAIL = "FORGOT_EMAIL";
    private static final String FORGOT_OTP_HASH = "FORGOT_OTP_HASH";
    private static final String FORGOT_OTP_EXP = "FORGOT_OTP_EXP";
    private static final String FORGOT_OK = "FORGOT_OK";
    private static final String FORGOT_LAST_SENT = "FORGOT_LAST_SENT";

    private static final SecureRandom RND = new SecureRandom();

    // ===== DRAFT dùng cho register OTP (lưu trong session để bước sau chỉ nhập OTP) =====
    public record RegisterDraft(String email, String passwordHash, String fullName) {}

    // =========================
    // REGISTER OTP (SESSION)
    // =========================

    @Transactional
    public void registerRequestOtp(AuthDtos.RegisterRequest req, HttpSession session) {
        String email = normalizeEmail(req.email());

        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("EMAIL_ALREADY_EXISTS");
        }

        checkResendCooldown(session, REG_LAST_SENT);

        RegisterDraft draft = new RegisterDraft(
                email,
                passwordEncoder.encode(req.password()),
                req.fullName()
        );
        session.setAttribute(REG_DRAFT, draft);

        String otp = genOtp6();
        session.setAttribute(REG_OTP_HASH, TokenUtil.sha256Hex(otp));
        session.setAttribute(REG_OTP_EXP, Instant.now().plus(otpMinutes, ChronoUnit.MINUTES));
        session.setAttribute(REG_LAST_SENT, Instant.now());

        sendOtpEmail(email, otp, "REGISTER");
    }

    @Transactional
    public void registerVerifyOtp(String otp, HttpSession session) {
        RegisterDraft draft = (RegisterDraft) session.getAttribute(REG_DRAFT);
        if (draft == null) throw new RuntimeException("REGISTER_DRAFT_MISSING");

        verifyOtpOrThrow(otp, session, REG_OTP_HASH, REG_OTP_EXP);

        // Public register => FIX ROLE = CUSTOMER
        User user = User.builder()
                .email(draft.email())
                .passwordHash(draft.passwordHash())
                .fullName(draft.fullName())
                .role(Role.CUSTOMER)
                .isActive(true)
                .emailVerified(true) // OTP verified => coi như verified
                .build();

        userRepository.save(user);

        // clear register session
        session.removeAttribute(REG_DRAFT);
        session.removeAttribute(REG_OTP_HASH);
        session.removeAttribute(REG_OTP_EXP);
    }

    // =========================
    // FORGOT PASSWORD OTP (SESSION)
    // =========================

    @Transactional
    public void forgotRequestOtp(AuthDtos.ForgotRequest req, HttpSession session) {
        String email = normalizeEmail(req.email());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));

        if (!user.isActive()) throw new RuntimeException("USER_DISABLED");

        checkResendCooldown(session, FORGOT_LAST_SENT);

        String otp = genOtp6();
        session.setAttribute(FORGOT_EMAIL, email);
        session.setAttribute(FORGOT_OTP_HASH, TokenUtil.sha256Hex(otp));
        session.setAttribute(FORGOT_OTP_EXP, Instant.now().plus(otpMinutes, ChronoUnit.MINUTES));
        session.setAttribute(FORGOT_OK, false);
        session.setAttribute(FORGOT_LAST_SENT, Instant.now());

        sendOtpEmail(email, otp, "FORGOT_PASSWORD");
    }

    public void forgotVerifyOtp(String otp, HttpSession session) {
        verifyOtpOrThrow(otp, session, FORGOT_OTP_HASH, FORGOT_OTP_EXP);
        session.setAttribute(FORGOT_OK, true);
    }

    @Transactional
    public void forgotResetPassword(AuthDtos.ResetPasswordRequest req, HttpSession session) {
        String email = (String) session.getAttribute(FORGOT_EMAIL);
        Boolean ok = (Boolean) session.getAttribute(FORGOT_OK);

        if (email == null) throw new RuntimeException("FORGOT_EMAIL_MISSING");
        if (ok == null || !ok) throw new RuntimeException("FORGOT_NOT_VERIFIED");

        if (!req.newPassword().equals(req.confirmNewPassword())) {
            throw new RuntimeException("PASSWORD_CONFIRM_NOT_MATCH");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        // logout all devices
        revokeAllUserRefreshTokens(user.getId());

        // clear forgot session
        session.removeAttribute(FORGOT_EMAIL);
        session.removeAttribute(FORGOT_OTP_HASH);
        session.removeAttribute(FORGOT_OTP_EXP);
        session.removeAttribute(FORGOT_OK);
    }

    // =========================
    // CHANGE PASSWORD (LOGGED IN)
    // =========================

    @Transactional
    public void changePassword(Long userId, AuthDtos.ChangePasswordRequest req) {
        if (!req.newPassword().equals(req.confirmNewPassword())) {
            throw new RuntimeException("PASSWORD_CONFIRM_NOT_MATCH");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));

        if (!user.isActive()) throw new RuntimeException("USER_DISABLED");

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new RuntimeException("CURRENT_PASSWORD_INVALID");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        revokeAllUserRefreshTokens(userId);
    }

    // =========================
    // LOGIN / REFRESH / LOGOUT (GIỮ NGUYÊN)
    // =========================

    @Transactional
    public LoginResult login(AuthDtos.LoginRequest req, HttpServletRequest httpReq) {

        String email = normalizeEmail(req.email());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("INVALID_CREDENTIALS"));

        if (!user.isActive()) throw new RuntimeException("USER_DISABLED");
        if (!user.isEmailVerified()) throw new RuntimeException("EMAIL_NOT_VERIFIED");

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new RuntimeException("INVALID_CREDENTIALS");
        }

        String access = jwtProvider.generateAccessToken(user.getId(), user.getRole());
        String rawRefresh = issueRefreshToken(user.getId(), httpReq);

        return new LoginResult(access, rawRefresh, user);
    }

    @Transactional
    public RefreshResult refresh(String rawRefreshToken, HttpServletRequest httpReq) {

        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new RuntimeException("REFRESH_TOKEN_MISSING");
        }

        String hash = TokenUtil.sha256Hex(rawRefreshToken);
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new RuntimeException("REFRESH_TOKEN_INVALID"));

        // reuse detection
        if (current.getRevokedAt() != null && current.getReplacedByTokenId() != null) {
            revokeAllUserRefreshTokens(current.getUserId());
            throw new RuntimeException("REFRESH_TOKEN_REUSED");
        }

        if (current.getRevokedAt() != null) throw new RuntimeException("REFRESH_TOKEN_REVOKED");
        if (current.getExpiresAt().isBefore(Instant.now())) throw new RuntimeException("REFRESH_TOKEN_EXPIRED");

        User user = userRepository.findById(current.getUserId())
                .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));

        if (!user.isActive()) throw new RuntimeException("USER_DISABLED");

        // rotate refresh token
        current.setLastUsedAt(Instant.now());
        current.setRevokedAt(Instant.now());

        String newRaw = TokenUtil.generateOpaqueToken(32);
        String newHash = TokenUtil.sha256Hex(newRaw);

        RefreshToken next = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .tokenHash(newHash)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(refreshDays, ChronoUnit.DAYS))
                .ip(getIp(httpReq))
                .userAgent(httpReq.getHeader("User-Agent"))
                .build();

        refreshTokenRepository.save(next);

        current.setReplacedByTokenId(next.getId());
        refreshTokenRepository.save(current);

        String newAccess = jwtProvider.generateAccessToken(user.getId(), user.getRole());
        return new RefreshResult(newAccess, newRaw);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;

        String hash = TokenUtil.sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            if (rt.getRevokedAt() == null) {
                rt.setRevokedAt(Instant.now());
                refreshTokenRepository.save(rt);
            }
        });
    }

    @Transactional
    public void logoutAll(Long userId) {
        revokeAllUserRefreshTokens(userId);
    }

    // =========================
    // HELPERS
    // =========================

    private String normalizeEmail(String email) {
        return email == null ? null : email.toLowerCase().trim();
    }

    private String issueRefreshToken(Long userId, HttpServletRequest httpReq) {
        String raw = TokenUtil.generateOpaqueToken(32);
        String hash = TokenUtil.sha256Hex(raw);

        RefreshToken rt = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash(hash)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(refreshDays, ChronoUnit.DAYS))
                .ip(getIp(httpReq))
                .userAgent(httpReq.getHeader("User-Agent"))
                .build();

        refreshTokenRepository.save(rt);
        return raw;
    }

    private void revokeAllUserRefreshTokens(Long userId) {
        var all = refreshTokenRepository.findAllByUserId(userId);
        Instant now = Instant.now();
        for (RefreshToken rt : all) {
            if (rt.getRevokedAt() == null) rt.setRevokedAt(now);
        }
        refreshTokenRepository.saveAll(all);
    }

    private String getIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private String genOtp6() {
        int n = RND.nextInt(1_000_000);
        return String.format("%06d", n);
    }

    private void sendOtpEmail(String to, String otp, String purpose) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("[Task Management] OTP - " + purpose);
        msg.setText("""
                Your OTP code is: %s

                This code will expire in %d minutes.
                If you did not request this, please ignore.
                """.formatted(otp, otpMinutes));
        mailSender.send(msg);
    }

    private void verifyOtpOrThrow(String otp, HttpSession session, String hashKey, String expKey) {
        String hash = (String) session.getAttribute(hashKey);
        Instant exp = (Instant) session.getAttribute(expKey);

        if (hash == null || exp == null) throw new RuntimeException("OTP_MISSING");
        if (Instant.now().isAfter(exp)) throw new RuntimeException("OTP_EXPIRED");

        String inputHash = TokenUtil.sha256Hex(otp);
        if (!inputHash.equals(hash)) throw new RuntimeException("OTP_INVALID");
    }

    private void checkResendCooldown(HttpSession session, String lastSentKey) {
        Instant last = (Instant) session.getAttribute(lastSentKey);
        if (last != null) {
            long sec = ChronoUnit.SECONDS.between(last, Instant.now());
            if (sec < resendSeconds) {
                throw new RuntimeException("OTP_RESEND_TOO_FAST");
            }
        }
    }

    public record LoginResult(String accessToken, String rawRefreshToken, User user) {}
    public record RefreshResult(String accessToken, String rawRefreshToken) {}
}
