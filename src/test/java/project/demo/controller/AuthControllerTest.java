package project.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import project.demo.dto.AuthDtos;
import project.demo.entity.User;
import project.demo.enums.Role;
import project.demo.repository.UserRepository;
import project.demo.security.JwtProvider;
import project.demo.security.SecurityConfig;
import project.demo.service.AuthService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static project.demo.support.TestSecurity.user;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "app.refresh.cookie-name=refresh_token",
        "app.refresh.cookie-path=/",
        "app.refresh.secure=false",
        "app.refresh.same-site=Lax",
        "app.refresh.days=7"
})
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // ===== MOCK BẮT BUỘC =====
    @MockitoBean
    AuthService authService;

    @MockitoBean
    JwtProvider jwtProvider;

    @MockitoBean
    UserRepository userRepository;

    // =========================
    // REGISTER OTP
    // =========================

    @Test
    void registerRequestOtp_shouldReturnOk() throws Exception {
        var req = new AuthDtos.RegisterRequest(
                "test@email.com",
                "password123",
                "Test User"
        );

        doNothing().when(authService).registerRequestOtp(any(), any());

        mockMvc.perform(post("/auth/register/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("OTP_SENT"));
    }

    @Test
    void registerVerifyOtp_shouldReturnOk() throws Exception {
        var req = new AuthDtos.VerifyOtpRequest("123456");

        doNothing().when(authService).registerVerifyOtp(anyString(), any());

        mockMvc.perform(post("/auth/register/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("REGISTERED_SUCCESS"));
    }

    // =========================
    // LOGIN
    // =========================

    @Test
    void login_shouldReturnTokenAndCookie() throws Exception {
        var req = new AuthDtos.LoginRequest(
                "test@email.com",
                "password123"
        );

        User user = User.builder()
                .id(1L)
                .email("test@email.com")
                .fullName("Test User")
                .role(Role.CUSTOMER)
                .emailVerified(true)
                .build();

        // 🔥 MOCK ĐÚNG KIỂU SERVICE TRẢ VỀ
        var loginResult = new AuthService.LoginResult(
                "access-token",
                "raw-refresh-token",
                user
        );

        when(authService.login(any(AuthDtos.LoginRequest.class), any(HttpServletRequest.class)))
                .thenReturn(loginResult);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.user.email").value("test@email.com"));
    }

    // =========================
    // REFRESH
    // =========================

    @Test
    void refresh_shouldReturnNewAccessToken() throws Exception {
        var refreshResult = new AuthService.RefreshResult(
                "new-access-token",
                "new-raw-refresh"
        );

        when(authService.refresh(anyString(), any()))
                .thenReturn(refreshResult);

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.accessToken").value("new-access-token"));
    }

    // =========================
    // CHANGE PASSWORD
    // =========================

    @Test
    void changePassword_shouldReturnOk() throws Exception {
        var req = new AuthDtos.ChangePasswordRequest(
                "oldPass",
                "newPassword123",
                "newPassword123"
        );

        doNothing().when(authService).changePassword(eq(1L), any());

        mockMvc.perform(patch("/auth/change-password")
                        .with(user()) // mock AuthenticationPrincipal
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("PASSWORD_CHANGED"));
    }
}
