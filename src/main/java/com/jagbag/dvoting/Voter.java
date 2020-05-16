package com.jagbag.dvoting;

import com.fasterxml.jackson.annotation.JsonGetter;
import org.hibernate.annotations.NaturalId;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
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
 * When someone changes their email account, the old email is saved and continues to be used until, again,
 * they do the confirmationCode email thing.
 * Only a Voter with allowedToVote set to true can submit chits to be signed. Thus only someone with allowedToVote
 * privilege can cast a vote (because only signed chits are accepted by the CTF.)
 * Passwords are never saved in cleartext. Submitted passwords are concatenated with the (user-specific) salt,
 * hashed, and then compared with the password hash. This mean that reading the database does not give you the
 * information you would need to log in as someone else.
 * TODO: A Voter may assign their "proxy" to another Voter.
 * Note: Hibernate is apparently not able to update the database from the previous schema to accommodate
 * this. Some kind of error trying to execute "alter table voter add column proxy_accepted boolean not null"
 * Manually running "alter table voter add column proxy_accepted boolean default FALSE not null" works.
 * Story: Voter opens a page or a box
 * with a drop-down list of all the authorized Voters who have not assigned their own vote to a proxy,
 * and who have fewer than 2 active proxy relationships where they are the proxy-holder.
 * Voter may pick one, and click on "Assign Proxy".
 * Info on the Voter will display "Proxy: (name) - not yet accepted".
 * Proxy gets email with a confirmation link, and a refusal link.
 * When/if the requested proxy clicks on the confirmation link, the proxy relationship
 * is activated. If the requested proxy clicks on the refusal link, the proxy request is canceled.
 * (Send email to the person who requested their proxy be held, with the news of what the other person
 * decided.)
 * At any time, the Voter who assigned their proxy can click on "Revoke Proxy".
 * Administration page should have another column, showing the proxy holder (greyed out if not active).
 * If a Voter has granted their proxy to another, and the acceptance has been confirmed, the Voter may
 * view questions, but not vote-- their ballot does NOT get chits signed by the CTF.
 * When the proxy HOLDER logs in, in addition to the VOTE here link, they have an additional link
 * for each person whose proxy they hold, i.e.:
 * VOTE here
 * VOTE here on David's behalf
 * VOTE here on Sandy's behalf
 * Same link but with a query paramter.
 * Voting page should announce whose proxy it's voting for.
 * Voting page submits chits for signing with the username not of the logged-in voter, but with the proxy grantee.
 * Future enhancement: proxy relationships having expiration
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
    @Transient
    private String newRandomPassword;
    private String resetPasswordSalt;
    private String resetPasswordHash;
    private String resetConfirmationCode;
/* Not yet implemnting proxies
    @ManyToOne
    @JoinColumn(name = "fk_proxy")
    private Voter proxyHolder;
    private boolean proxyAccepted;
    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_proxy")
    public List<Voter> proxyGrantees;
*/

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
    public void setUsername(String newUsername) { this.username = newUsername; } // TODO: test: does this wreak havoc?
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
/* Not yet implementing granting proxies
    public Voter getProxyHolder() { return proxyHolder; }
    public boolean isProxyAccepted() { return proxyAccepted; }
    public List<Voter> getProxyGrantees() { return proxyGrantees; }  */

    /**
     * Come up with a new ugly random password that a user can use if they can't remember their password.
     * When a user requests that their password be reset, we come up with a random password for them. This is not
     * yet their new password; the old password is expected until and and unless they click on the reset link that
     * they will be sent in email. (Of course, we don't save the password in cleartext in the database, not ever,
     * but rather create a new salt that is saved and save the hashed salted password.)
     * In the course of this session, the user will be sent an email that includes their new password-- it's a
     * transient field, so we have the actual new password in memory for the moment-- and a confirmation code.
     * @see Voter#confirmPasswordReset for what happens if/when they click on the link to finalize the password
     * reset.
     * @throws UnsupportedEncodingException
     * @throws NoSuchAlgorithmException
     */
    public void prepareForReset() throws UnsupportedEncodingException, NoSuchAlgorithmException {
        newRandomPassword = (new BigInteger(48, ThreadLocalRandom.current())).toString(36);
        resetPasswordSalt = (new BigInteger(48, ThreadLocalRandom.current())).toString(36);
        resetPasswordHash = hashWithSalt(newRandomPassword, resetPasswordSalt);
        resetConfirmationCode = (new BigInteger(48, ThreadLocalRandom.current())).toString(36);
    }

    /**
     * Switch over to the randomly-generated new password because the user, apparently, forgot their old one.
     * @param code - confirmation code that the user was sent in their email.
     * This method MUST be called from an object that can persist this change to the database.
     */
    public void confirmPasswordReset(String code) {
        if (code.equals(resetConfirmationCode)) {
            passwordHash = resetPasswordHash;
            passwordSalt = resetPasswordSalt;
            resetConfirmationCode = null;
        }
    }

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

    public void submitEmailChange(String newEmail) {
        if (!newEmail.equals(email)) {
            prepareForConfirmationEmail();
            oldEmail = getEmail();
            email = newEmail;
            emailConfirmed = false;
        }
    }

    public void prepareForConfirmationEmail() {
        confirmationCode = (new BigInteger(48, ThreadLocalRandom.current())).toString(36);
    }

    public boolean canConfirmCode(String code) {
        return code.equals(getConfirmationCode());
    }

    public boolean confirmEmail(String code) {
        if (canConfirmCode(code)) {
            emailConfirmed = true;
        }
        return emailConfirmed;
    }

    public void setEmailConfirmed(boolean flag) {
        emailConfirmed = flag;
    }

    /**
     * Fills in variables in email templates.
     * Used for both the new account confirmation email and the reset password email, so deals with a superset
     * of the variables involved in those processes.
     * Note that some variables are handled by LoginController instead of here.
     * @param text
     * @return
     */
    public String processEmailText(String text) throws UnsupportedEncodingException {
        text = text.replaceAll("##USERNAME##", getUsername());
        text = text.replaceAll("##QUERYUSER##", URLEncoder.encode(getUsername(), StandardCharsets.UTF_8.toString()));
        text = text.replaceAll("##EMAIL##", getEmail());
        text = text.replaceAll("##OLDEMAIL##", getOldEmail());
        text = text.replaceAll("##NAME##", getName());
        text = text.replaceAll("##CODE##", getConfirmationCode());
        text = text.replaceAll("##RESET##", resetConfirmationCode);
        text = text.replaceAll("##PASS##", newRandomPassword);
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

    public void invalidateConfirmationCode() {
        confirmationCode = null;
    }
}
