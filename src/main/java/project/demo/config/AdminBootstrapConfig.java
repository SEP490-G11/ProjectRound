package project.demo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import project.demo.entity.User;
import project.demo.enums.Role;
import project.demo.repository.UserRepository;

@Configuration
@RequiredArgsConstructor
public class AdminBootstrapConfig {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap-admin.email:admin@local.test}")
    private String adminEmail;

    @Value("${app.bootstrap-admin.password:Admin@123456}")
    private String adminPassword;

    @Value("${app.bootstrap-admin.full-name:System Admin}")
    private String adminFullName;

    @Bean
    public ApplicationRunner seedAdminIfNeeded() {
        return args -> {
            if (userRepository.count() > 0) return;

            User admin = User.builder()
                    .email(adminEmail.toLowerCase().trim())
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .fullName(adminFullName)
                    .role(Role.ADMIN)
                    .isActive(true)
                    .emailVerified(true) // bootstrap admin coi như verified
                    .build();

            userRepository.save(admin);

            System.out.println("[BOOTSTRAP] Admin created: " + adminEmail + " / " + adminPassword);
        };
    }
}
