package project.demo.support;

import project.demo.entity.User;
import project.demo.security.CustomUserDetails;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;


public class TestSecurity {

    public static CustomUserDetails mockUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("test@local.test");
        u.setPasswordHash("password");
        u.setRole(project.demo.enums.Role.CUSTOMER);
        u.setActive(true);
        u.setEmailVerified(true);

        return new CustomUserDetails(u);
    }



    public static RequestPostProcessor user() {
        return SecurityMockMvcRequestPostProcessors.user(mockUser());
    }
}
