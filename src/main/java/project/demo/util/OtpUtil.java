package project.demo.util;

import java.security.SecureRandom;

public class OtpUtil {
    private static final SecureRandom RND = new SecureRandom();

    public static String gen6() {
        int n = RND.nextInt(1_000_000);
        return String.format("%06d", n);
    }
}
