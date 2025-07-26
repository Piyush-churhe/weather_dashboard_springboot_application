package com.example.weatherdashboard.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    
    @Autowired(required = false)
    private JavaMailSender mailSender;

    public void sendSimpleMail(String to, String subject, String text) {
        // If JavaMailSender is not available, use mock mode
        if (mailSender == null) {
            logger.warn("JavaMailSender not available - using mock mode");
            logger.info("=== MOCK EMAIL SENT ===");
            logger.info("To: {}", to);
            logger.info("Subject: {}", subject);
            logger.info("Body: {}", text);
            logger.info("=== END MOCK EMAIL ===");
            return;
        }
        try {
            logger.info("=== SENDING EMAIL ===");
            logger.info("To: {}", to);
            logger.info("Subject: {}", subject);
            logger.info("Text length: {}", text.length());
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            
            logger.info("Mail message created, attempting to send...");
            mailSender.send(message);
            logger.info("Email sent successfully to: {}", to);
            
        } catch (Exception e) {
            logger.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }
} 