package com.skillhub.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender emailSender;

    @org.springframework.beans.factory.annotation.Value("${spring.mail.username}")
    private String fromEmail;

    @Override
    public void sendSimpleMessage(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            emailSender.send(message);
            System.out.println("Email sent to " + to + ": " + text); // Log for verification if smtp fails or is mocked
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
            // In a real app, we might want to rethrow or handle differently, but for now we
            // log and proceed
            // to allow testing flow even without valid SMTP config.
            System.out.println("MOCK EMAIL SENDER (Fallback):");
            System.out.println("To: " + to);
            System.out.println("Subject: " + subject);
            System.out.println("Body: " + text);
        }
    }
}
