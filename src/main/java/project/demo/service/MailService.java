package project.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailService {
    private final JavaMailSender mailSender;

    public void sendOtp(String to, String otp, String purpose) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("[Task Management] OTP - " + purpose);
        msg.setText("""
                Your OTP code is: %s
                
                This code will expire soon. If you did not request this, please ignore.
                """.formatted(otp));
        mailSender.send(msg);
    }
}
