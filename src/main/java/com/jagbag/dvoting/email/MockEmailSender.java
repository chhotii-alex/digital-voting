package com.jagbag.dvoting.email;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Mock interface with email, for development purposes, when we can't actually send email.
 */
@Component("emailSender")
@Profile("!prod")
public class MockEmailSender implements EmailSender {

    public boolean isConfiguredForEmail() {
        return true; // never sends email, but for dev, will always PRETEND to
    }

    @Override
    public void sendEmail(String email, String subject, String text) {
        System.out.println("To: " + email);
        System.out.println("Subject: " + subject);
        System.out.println(text);
    }
}
