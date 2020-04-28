package com.jagbag.dvoting;

/**
 * Interface to the email system, for sending out emails to users.
 */
public interface EmailSender {
    // TODO do a real-life implementation; at this point I just have a mock
    void sendEmail(String email, String subject, String text);
}
