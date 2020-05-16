package com.jagbag.dvoting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * This object is in charge of validating the login credentials from the login page; generating, managing,
 * and validating the cookie token used for session continuity; and validating authorization for all endpoints,
 * based on cookies sent in message headers. It works closely with the LoginController&mdash; which handles the
 * login-related endpoints&mdash; and the VoterListManager, our interface with the list of users kept in the
 * database.
 */
@Component("loginManager")
public class LoginManager {
    /** Text template for the body of the reset password email. */
    @Value("classpath:static/reset.html")
    protected Resource resetEmailTemplate;

    /** Text template for the body of the email announcing that your account was created by admin. */
    @Value("classpath:static/announceaccount.txt")
    protected Resource announceEmailTemplate;

    /** See {@link VoterListManager } */
    @Autowired protected VoterListManager voterListManager;
    private Map<String, String> tokens = new HashMap<String, String>();
    private Map<String, Long> backoffPerLogin = new HashMap<String, Long>();

    public Voter validateLoginCredentials(String username, String password) {
        Voter v = voterListManager.getForUsername(username);
        if (v == null) {
            return null;
        }
        try {
            if (v.checkPassword(password)) {
                createTokenForUser(username);
                return v;
            }
            else {
                // exponential backoff, to mitigate dictionary attack
                Thread.currentThread().sleep(incrementBackoffForUser(username));
                return null;
            }
        }
        catch (Exception ex) {
            // Theoretically checkPassword() throws exceptions, but this would be a big wtf exception.
            // TODO: Log such a disasterous wtf correctly
            ex.printStackTrace();
            return null;
        }
    }

    protected synchronized void clearBackoffForUser(String username) {
        backoffPerLogin.put(username, 50L);
    }

    protected synchronized long incrementBackoffForUser(String username) {
        long delay = backoffPerLogin.computeIfAbsent(username, (String u) -> {return 50L;});
        long newDelay = (long)(delay * (ThreadLocalRandom.current().nextDouble()+1) );
        backoffPerLogin.put(username, newDelay);
        return delay;
    }

    protected synchronized void createTokenForUser(String username) {
        String newToken = (new BigInteger(24, ThreadLocalRandom.current())).toString(36);
        tokens.put(username, newToken);
    }

    protected synchronized boolean validateTokenForUser(String token, String username) {
        return (token.equals(tokenForUser(username)));
    }

    protected synchronized String tokenForUser(String username) {
        return tokens.get(username);
    }

    public synchronized void forgetTokenForUser(Voter v) {
        tokens.remove(v.getUsername());
    }

    public Voter validateBasicUser(HttpHeaders headers) {
        String username = null;
        String token = null;
        if (headers.get(HttpHeaders.COOKIE) != null) {
            for (String cookieHeader : headers.get(HttpHeaders.COOKIE)) {
                String cookies[] = cookieHeader.split(";");
                for (String cookie : cookies) {
                    String fields[] = cookie.trim().split("=");
                    if (fields.length < 2) { continue; }  // no value
                    switch (fields[0]) {
                        case "user":
                            username = fields[1];
                            break;
                        case "token":
                            token = fields[1];
                            break;
                    }
                }
            }
        }
        if (username == null || token == null) {
            throw new UnauthorizedException();
        }
        if (!validateTokenForUser(token, username)) {
            throw new UnauthorizedException();
        }
        Voter v = voterListManager.getForUsername(username);
        if (v == null) {
            throw new UnauthorizedException();
        }
        if (!v.isActiveAccount()) {
            throw new UnauthorizedException();
        }
        return v;
    }

    public Voter validateVotingUser(HttpHeaders headers) {
        Voter v = validateBasicUser(headers);
        if ((v == null) || (!v.isAllowedToVote())) {
            throw new ForbiddenException();
        }
        return v;
    }

    public Voter validateAdminUser(HttpHeaders headers) {
        Voter v = validateBasicUser(headers);
        if ((v == null) || (!v.isAdmin())) {
            throw new ForbiddenException();
        }
        return v;
    }

    /*
    A user is privileged if they either have admin status or can vote.
     */
    public Voter validatePrivilegedUser(HttpHeaders headers) {
        Voter v = validateBasicUser(headers);
        if (v == null) {
            throw new ForbiddenException();
        }
        if (!v.isAdmin() && !v.isAllowedToVote()) {
            throw new ForbiddenException();
        }
        return v;
    }

    public Voter validateCanAdministerUser(HttpHeaders headers, String username) {
        Voter v = validateBasicUser(headers);
        if (v == null) {
            throw new ForbiddenException();
        }
        if (!(v.isAdmin() || v.getUsername().equals(username))) {
            throw new ForbiddenException();
        }
        return v;
    }

    /* TODO: currently not a good job of reporting errors to the user */
    public boolean parseUploadedVoterList(MultipartFile file) {
        String emailPattern = "[a-zA-Z_0-9.]+(\\+[a-zA-Z_0-9.]+)?@\\w+\\.[a-zA-Z.]+";
        String namePattern = "[\\p{IsAlphabetic}\\p{Digit} .,-]+";
        InputStream in = null;
        InputStreamReader inRead = null;
        BufferedReader read = null;
        Map<String, String> peopleWhoShouldVote = new HashMap<String, String>();
        try {
            in = file.getInputStream();
            inRead = new InputStreamReader(in);
            read = new BufferedReader(inRead);
            String line;
            while (true) {
                line = read.readLine();
                if (line == null) break;
                if (line.length() < 3) {
                    System.out.println("Weirdly short line in uploaded voter list file: " + line);
                    continue;
                }
                if (line.length() > 80) {
                    System.out.println("Weirdly long line in uploaded voter list file: " + line);
                    System.out.println("Rejecting file.");
                    return false;
                }
                line = line.trim();
                String[] fields = line.split("\t");
                if (fields.length != 2) {
                    System.out.println("Each line of an uploaded voter list file should contain 2 tab-separated fields.");
                    System.out.println("Skipping malformed line: " + line);
                    continue;
                }
                String email = fields[0];
                String name = fields[1];
                if (!email.matches(emailPattern)) {
                    System.out.println("Bad email, skipped: " + email);
                    continue;
                }
                name.replaceAll("[<>'\"\\/:;=]", "");
                if (!name.matches(namePattern)) {
                    System.out.println("Bad name, skipped: " + fields[1]);
                    continue;
                }
                if (peopleWhoShouldVote.get(email) != null) {
                    System.out.println("Duplicate email address in data: " + email);
                    continue;
                }
                peopleWhoShouldVote.put(email, name);
            }  // END while
            if (peopleWhoShouldVote.keySet().size() > 0) {
                updateVoterList(peopleWhoShouldVote);
            }
            else {
                System.out.println("Zero valid records; doing nothing");
                return false;
            }
        }
        catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            return false;
        }
        finally {
            try {
                if (read != null) {
                    read.close();
                }
                if (inRead != null) {
                    inRead.close();
                }
                if (in != null) {
                    in.close();
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    /** Connection to the email sending system */
    @Autowired protected EmailSender emailSender;

    /**
     * Send the email to the user that allows them to reset their password.
     * @param v - person whose password is being reset
     * @return
     */
    protected boolean sendResetEmail(Voter v) {
        try {
            String text = v.processEmailText(APIController.textFromResource(resetEmailTemplate));
            text = text.replaceAll("##BASEURL##", hostBaseURL);
            emailSender.sendEmail(v.getEmail(), "your password reset", text);
            return true;
        }
        catch (Exception ex) {
            System.err.println("Error sending reset password email");
            ex.printStackTrace();
            return false;
        }
    }

    /** Text template for the body of the email to be sent asking users to confirm their email address. */
    @Value("classpath:static/confirmemail.html")
    protected Resource confirmEmailTemplate;

    public boolean sendConfirmationEmail(Voter v) {
        try {
            String text = v.processEmailText(APIController.textFromResource(confirmEmailTemplate));
            // TODO: how do we get the actual URL we're running at?
            text = text.replaceAll("##BASEURL##", hostBaseURL);
            emailSender.sendEmail(v.getEmail(), "please confirm your email", text);
            return true;
        }
        catch (Exception ex) {
            ex.printStackTrace();
            // TODO: how to log errors here correctly?
            return false;
        }
    }


    @Value( "${base-url}" )
    public String hostBaseURL;

    /**
     * Send the email to a user whose account was created due to a bulk upload by admin
     * @param v - person with new account
     * @return
     */
    public boolean sendAutomaticallyAddedAccountEmail(Voter v) {
        try {
            String text = v.processEmailText(APIController.textFromResource(announceEmailTemplate));
            text = text.replaceAll("##BASEURL##", hostBaseURL);
            emailSender.sendEmail(v.getEmail(), "Digital Voting account", text);
            return true;
        }
        catch (Exception ex) {
            System.err.println("Error sending account-added email");
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * Update Voter table such that the set of Voter records with allowedToVote set to true
     * exactly matches the contents of this data structure.
     * For each email - name pair, two possibilities:
     * - there is an account with the given email address. If so turn on allowedToVote.
     * - there is no account with the given email address. Make an account, email the person, and
     *      turn on allowedToVote with their login credentials.
     * Then, turn off voting for each Voter not in this data structure. Query for the list of Voters
     * who are allowedToVote, and for each one, if they're not listed, turn that off.
     * @param peopleWhoShouldVote Map with email address as key, real person name as value
     */
    private void updateVoterList(Map<String, String> peopleWhoShouldVote) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        Set<Voter> updatedVoters = new HashSet<Voter>();
        for (String email : peopleWhoShouldVote.keySet()) {
            Voter v = voterListManager.getForEmail(email);
            if (v == null) {
                v = new Voter(peopleWhoShouldVote.get(email), email, email);
                String tempPassword = (new BigInteger(48, ThreadLocalRandom.current())).toString(36);
                voterListManager.addVoter(v, tempPassword);
                voterListManager.activateAccountWithoutConfirm(v);
                v.setAllowedToVote(true);
                v.prepareForReset();
                sendAutomaticallyAddedAccountEmail(v);
                updatedVoters.add(v);
            }
            else {
                if (!v.isActiveAccount()) {
                    voterListManager.activateAccountWithoutConfirm(v);
                    updatedVoters.add(v);

                }
                if (!v.isAllowedToVote()) {
                    v.setAllowedToVote(true);
                    updatedVoters.add(v);
                }
            }
        }
        for (Voter v : voterListManager.voters()) {
            if (v.isAllowedToVote()) {
                if (peopleWhoShouldVote.get(v.getEmail()) == null) {
                    v.setAllowedToVote(false);
                    updatedVoters.add(v);
                }
            }
        }
        voterListManager.updateVoters(updatedVoters);
    }
}
