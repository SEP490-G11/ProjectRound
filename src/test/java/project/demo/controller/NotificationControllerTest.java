package project.demo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import project.demo.repository.UserRepository;
import project.demo.security.JwtProvider;
import project.demo.service.NotificationService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static project.demo.support.TestSecurity.user;

@WebMvcTest(NotificationController.class)
@Import(project.demo.security.SecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    MockMvc mockMvc;

    //Mock service đúng cách
    @MockitoBean
    NotificationService notificationService;

    //MOCK BẮT BUỘC – FIX LỖI CỦA BẠN
    @MockitoBean
    JwtProvider jwtProvider;

    @MockitoBean
    UserRepository userRepository;
    @Test
    void myNotifications_shouldReturnOk() throws Exception {
        when(notificationService.listMyNotifications(
                eq(1L),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(Page.empty());

        mockMvc.perform(get("/notifications")
                        .with(user()))
                .andExpect(status().isOk());
    }

    @Test
    void markRead_shouldReturnOk() throws Exception {
        doNothing().when(notificationService).markRead(1L, 10L);

        mockMvc.perform(patch("/notifications/10/read")
                        .with(user()))
                .andExpect(status().isOk());
    }

    @Test
    void markAllRead_shouldReturnOk() throws Exception {
        doNothing().when(notificationService).markAllRead(1L);

        mockMvc.perform(patch("/notifications/read-all")
                        .with(user()))
                .andExpect(status().isOk());
    }
}
