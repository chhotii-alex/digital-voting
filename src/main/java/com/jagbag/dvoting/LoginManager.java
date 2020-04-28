package com.jagbag.dvoting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
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
    /** See {@link VoterListManager } */
    @Autowired protected VoterListManager voterListManager;
    private Map<String, String> tokens = new HashMap<String, String>();

    public boolean validateLoginCredentials(String username, String password) {
        Voter v = voterListManager.getForUsername(username);
        if (v == null) {
            return false;
        }
        if (!v.isEmailConfirmed()) {
            throw new UnauthorizedException();
        }
        try {
            if (v.checkPassword(password)) {
                createTokenForUser(username);
                return true;
            } else {
                return false;
            }
        }
        catch (Exception ex) {
            // Theoretically checkPassword() throws exceptions, but this would be a big wtf exception.
            // TODO: Log such a disasterous wtf correctly
            ex.printStackTrace();
            return false;
        }
    }

    public synchronized void createTokenForUser(String username) {
        String newToken = (new BigInteger(24, ThreadLocalRandom.current())).toString(36);
        tokens.put(username, newToken);
    }

    public synchronized boolean validateTokenForUser(String token, String username) {
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
            return null;
        }
        if (!v.isEmailConfirmed()) {
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
}
