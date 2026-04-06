package com.ai.scheduler.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender, @Value("${spring.mail.username}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendVerificationEmail(String recipient, String token, String appBaseUrl) {
        String verifyUrl = appBaseUrl + "/api/auth/verify-email?token=" + token;
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromAddress);
        msg.setTo(recipient);
        msg.setSubject("Verify your AI Scheduler account");
        msg.setText("Please verify your account by clicking this link: " + verifyUrl);
        mailSender.send(msg);
    }
}
