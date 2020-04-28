package com.jagbag.dvoting;

import org.springframework.stereotype.Component;

/**
 * Mock interface with email, for development purposes, when we can't actually send email.
 */
@Component("emailSender")
public class MockEmailSender implements EmailSender {
    @Override
    public void sendEmail(String email, String subject, String text) {
        System.out.println("To: " + email);
        System.out.println("Subject: " + subject);
        System.out.println(text);
    }
}
