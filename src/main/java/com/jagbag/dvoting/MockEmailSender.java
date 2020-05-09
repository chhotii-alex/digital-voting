package com.jagbag.dvoting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Mock interface with email, for development purposes, when we can't actually send email.
 */
public class MockEmailSender implements EmailSender {
    @Override
    public void sendEmail(String email, String subject, String text) {
        System.out.println("To: " + email);
        System.out.println("Subject: " + subject);
        System.out.println(text);
    }
}
