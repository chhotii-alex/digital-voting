package com.jagbag.dvoting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * See this super-basic and simple tutorial: https://www.baeldung.com/spring-email
 * SMTP parameters must be set in application.properties for this to work.
 * If using gmail, I've found that I need to set "Allow less secure apps" to ON.
 * Presumably, when deployed to an actual virtual host service, there will be provision
 * to securely connect to that virtual host's SMTP server.
 */
@Component("emailSender")
public class SimpleEmailSender implements EmailSender {
    @Autowired
    public JavaMailSender emailSender;

    @Override
    public void sendEmail(String email, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject(subject);
        message.setText(text);
        emailSender.send(message);
    }
}
