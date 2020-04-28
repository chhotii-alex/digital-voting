package com.jagbag.dvoting;

import com.fasterxml.jackson.annotation.JsonGetter;
import org.hibernate.annotations.NaturalId;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ThreadLocalRandom;
import javax.validation.constraints.*;
import javax.persistence.*;
import javax.xml.bind.DatatypeConverter;

/**
 * Instances of this class represent user accounts of people who may be authorized voters or just potential voters.
 * Fields related to the account holder's real-life identity, and communication with them:
 * name, email, oldEmail
 * Fields related to the process of logging in:
 * username, passwordHash, passwordSalt
 * Fields related to privileges the account holder has:
 * allowedToVote, admin
 * Fields related to the process of confirming and updating email address:
 * email, oldEmail, confirmationCode
 * When someone creates a new account, this immediately creates a record in the database, but they cannot log
 * in, nor do they show up on the admin's list to grant privileges to, untill they have clicked on the link
 * in an email that they receive with the confirmationCode in the query.
 * TODO When someone changes their email account, the old email is saved and continues to be used until, again,
 * they do the confirmationCode email thing.
 * Only a Voter with allowedToVote set to true can submit chits to be signed. Thus only someone with allowedToVote
 * privilege can cast a vote (because only signed chits are accepted by the CTF.)
 * Passwords are never saved in cleartext. Submitted passwords are concatenated with the (user-specific) salt,
 * hashed, and then compared with the password hash. This mean that reading the database does not give you the
 * information you would need to log in as someone else.
 */
@Entity
public class Voter {
    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private long id;
    private String name;
    @NaturalId
    private String username;
    // TODO: enforce validation of email
    @Email private String email;
    @Email private String oldEmail;
    private boolean allowedToVote;
    private boolean admin;
    private String passwordSalt;
    private String passwordHash;
    private boolean emailConfirmed;
    private String confirmationCode;

    protected Voter() {} // Hibernate needs this

    public Voter(String name, String username, String email) {
        this.name = name;
        this.username = username;
        this.email = email;
        this.allowedToVote = false;
        this.admin = false;
        this.emailConfirmed = false;
    }

    public long getId() { return id; }
    public void setId(long newID) { this.id = newID; }
    public String getName() { return name; }
    public void setName(String newName) { this.name = newName; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public void setEmail(String newEmail) { this.email = newEmail; }
    public String getOldEmail() { return oldEmail; }
    public void setOldEmail(String theEmail) { this.oldEmail = theEmail; }
    public boolean isAllowedToVote() { return this.allowedToVote; }
    public void setAllowedToVote(boolean flag) { this.allowedToVote = flag; }
    public boolean isAdmin() { return this.admin; }
    public void setAdmin(boolean flag) { this.admin = flag; }
    private String getConfirmationCode() { return this.confirmationCode; }
    public boolean isEmailConfirmed() { return emailConfirmed; }

    public void setPassword(String password) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        passwordSalt = (new BigInteger(48, ThreadLocalRandom.current())).toString(36);
        passwordHash = hashWithSalt(password, passwordSalt);
    }

    private String hashWithSalt(String password, String salt) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        String concat = password + salt;
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(concat.getBytes("UTF-8"));
        String hashStr = DatatypeConverter.printHexBinary(hash);
        return hashStr;
    }

    public boolean checkPassword(String password) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        String hashStr = hashWithSalt(password, passwordSalt);
        return (hashStr.equals(passwordHash));
    }

    public void prepareForConfirmationEmail() {
        confirmationCode = (new BigInteger(48, ThreadLocalRandom.current())).toString(36);
    }

    public boolean canConfirmCode(String code) {
        return code.equals(getConfirmationCode());
    }

    public void confirmEmail(String code) {
        if (canConfirmCode(code)) {
            emailConfirmed = true;
        }
    }

    public void setEmailConfirmed(boolean flag) {
        emailConfirmed = flag;
    }

    public String processConfirmationEmailText(String text) {
        text = text.replaceAll("##USERNAME##", getUsername());
        text = text.replaceAll("##NAME##", getName());
        text = text.replaceAll("##CODE##", getConfirmationCode());
        return text;
    }

    /* If it's a new account and the email has never been confirmed, it's not yet active,
    * and will be suppressed from the admin's list. */
    public boolean isActiveAccount() {
        return (emailConfirmed || (oldEmail != null));
    }

    @JsonGetter("currentEmail")
    public String getCurrentEmail() {
        if (emailConfirmed) {
            return email;
        }
        else {
            return oldEmail;
        }
    }

}
