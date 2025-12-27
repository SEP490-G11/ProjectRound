package project.demo.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import project.demo.entity.User;
import project.demo.enums.Role;
import project.demo.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapConfigTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    @Test
    void seedAdminIfNeeded_whenEmpty_shouldCreateAdmin() throws Exception {
        AdminBootstrapConfig cfg = new AdminBootstrapConfig(userRepository, passwordEncoder);

        // inject @Value fields manually
        ReflectionTestUtils.setField(cfg, "adminEmail", "admin@local.test");
        ReflectionTestUtils.setField(cfg, "adminPassword", "Admin@123456");
        ReflectionTestUtils.setField(cfg, "adminFullName", "System Admin");

        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("Admin@123456")).thenReturn("hashed");

        ApplicationArguments args = new DefaultApplicationArguments(new String[]{});
        cfg.seedAdminIfNeeded().run(args);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertEquals("admin@local.test", saved.getEmail());
        assertEquals("hashed", saved.getPasswordHash());
        assertEquals("System Admin", saved.getFullName());
        assertEquals(Role.ADMIN, saved.getRole());
        assertTrue(saved.isActive());
        assertTrue(saved.isEmailVerified());
    }

    @Test
    void seedAdminIfNeeded_whenNotEmpty_shouldDoNothing() throws Exception {
        AdminBootstrapConfig cfg = new AdminBootstrapConfig(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(cfg, "adminEmail", "admin@local.test");
        ReflectionTestUtils.setField(cfg, "adminPassword", "Admin@123456");
        ReflectionTestUtils.setField(cfg, "adminFullName", "System Admin");

        when(userRepository.count()).thenReturn(5L);

        cfg.seedAdminIfNeeded().run(new DefaultApplicationArguments(new String[]{}));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }
}
