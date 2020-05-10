package com.jagbag.dvoting;

/**
 * Interface to the email system, for sending out emails to users.
 */
public interface EmailSender {
    public boolean isConfiguredForEmail();
    void sendEmail(String email, String subject, String text);
}
