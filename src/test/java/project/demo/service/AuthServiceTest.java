package project.demo.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import project.demo.dto.AuthDtos;
import project.demo.entity.RefreshToken;
import project.demo.entity.User;
import project.demo.enums.Role;
import project.demo.repository.RefreshTokenRepository;
import project.demo.repository.UserRepository;
import project.demo.security.JwtProvider;
import project.demo.util.TokenUtil;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtProvider jwtProvider;

    @Mock
    JavaMailSender mailSender;

    @Mock
    HttpSession session;

    @Mock
    HttpServletRequest request;

    @InjectMocks
    AuthService authService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(authService, "refreshDays", 7);
        ReflectionTestUtils.setField(authService, "otpMinutes", 10);
        ReflectionTestUtils.setField(authService, "resendSeconds", 30);
    }

    // ================= REGISTER =================

    @Test
    void registerRequestOtp_success() {
        var req = new AuthDtos.RegisterRequest(
                "test@email.com", "123456", "Test User"
        );

        when(userRepository.existsByEmail("test@email.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        authService.registerRequestOtp(req, session);

        verify(session).setAttribute(eq("REG_DRAFT"), any());
        verify(session).setAttribute(eq("REG_OTP_HASH"), any());
        verify(session).setAttribute(eq("REG_OTP_EXP"), any());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void registerRequestOtp_emailExists_throw() {
        when(userRepository.existsByEmail(any())).thenReturn(true);

        assertThrows(RuntimeException.class, () ->
                authService.registerRequestOtp(
                        new AuthDtos.RegisterRequest("a@a.com", "1", "A"),
                        session
                )
        );
    }

    @Test
    void registerVerifyOtp_success() {
        AuthService.RegisterDraft draft =
                new AuthService.RegisterDraft("a@a.com", "hash", "A");

        when(session.getAttribute("REG_DRAFT")).thenReturn(draft);
        when(session.getAttribute("REG_OTP_HASH"))
                .thenReturn(TokenUtil.sha256Hex("123456"));
        when(session.getAttribute("REG_OTP_EXP"))
                .thenReturn(Instant.now().plusSeconds(60));

        authService.registerVerifyOtp("123456", session);

        verify(userRepository).save(any(User.class));
    }

    // ================= FORGOT PASSWORD =================

    @Test
    void forgotRequestOtp_success() {
        User user = User.builder()
                .id(1L)
                .email("a@a.com")
                .isActive(true)
                .build();

        when(userRepository.findByEmail(any()))
                .thenReturn(Optional.of(user));

        authService.forgotRequestOtp(
                new AuthDtos.ForgotRequest("a@a.com"),
                session
        );

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void forgotVerifyOtp_success() {
        when(session.getAttribute("FORGOT_OTP_HASH"))
                .thenReturn(TokenUtil.sha256Hex("111111"));
        when(session.getAttribute("FORGOT_OTP_EXP"))
                .thenReturn(Instant.now().plusSeconds(60));

        authService.forgotVerifyOtp("111111", session);

        verify(session).setAttribute("FORGOT_OK", true);
    }

    @Test
    void forgotResetPassword_success() {
        User user = User.builder()
                .id(1L)
                .email("a@a.com")
                .build();

        when(session.getAttribute("FORGOT_EMAIL")).thenReturn("a@a.com");
        when(session.getAttribute("FORGOT_OK")).thenReturn(true);
        when(userRepository.findByEmail(any()))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.encode(any())).thenReturn("newHash");
        when(refreshTokenRepository.findAllByUserId(any()))
                .thenReturn(List.of());

        authService.forgotResetPassword(
                new AuthDtos.ResetPasswordRequest("123", "123"),
                session
        );

        verify(userRepository).save(user);
    }

    // ================= CHANGE PASSWORD =================

    @Test
    void changePassword_success() {
        User user = User.builder()
                .id(1L)
                .passwordHash("oldHash")
                .isActive(true)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "oldHash")).thenReturn(true);
        when(passwordEncoder.encode(any())).thenReturn("newHash");
        when(refreshTokenRepository.findAllByUserId(1L))
                .thenReturn(List.of());

        authService.changePassword(1L,
                new AuthDtos.ChangePasswordRequest(
                        "old", "new", "new"
                ));

        verify(userRepository).save(user);
    }

    // ================= LOGIN =================

    @Test
    void login_success() {
        User user = User.builder()
                .id(1L)
                .email("a@a.com")
                .passwordHash("hash")
                .role(Role.CUSTOMER)
                .isActive(true)
                .emailVerified(true)
                .build();

        when(userRepository.findByEmail(any()))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any()))
                .thenReturn(true);
        when(jwtProvider.generateAccessToken(any(), any()))
                .thenReturn("access");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        AuthService.LoginResult result =
                authService.login(
                        new AuthDtos.LoginRequest("a@a.com", "123"),
                        request
                );

        assertNotNull(result.accessToken());
        assertNotNull(result.rawRefreshToken());
    }

    // ================= REFRESH =================

    @Test
    void refresh_success() {
        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(1L)
                .tokenHash(TokenUtil.sha256Hex("raw"))
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        User user = User.builder()
                .id(1L)
                .role(Role.CUSTOMER)
                .isActive(true)
                .build();

        when(refreshTokenRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));
        when(jwtProvider.generateAccessToken(any(), any()))
                .thenReturn("access");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        AuthService.RefreshResult result =
                authService.refresh("raw", request);

        assertNotNull(result.accessToken());
        assertNotNull(result.rawRefreshToken());
    }

    // ================= LOGOUT =================

    @Test
    void logout_success() {
        RefreshToken token = RefreshToken.builder().build();

        when(refreshTokenRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(token));

        authService.logout("raw");

        verify(refreshTokenRepository).save(any());
    }
}
