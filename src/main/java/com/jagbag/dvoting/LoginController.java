package com.jagbag.dvoting;

import java.io.*;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.*;
import javax.persistence.Transient;
import javax.validation.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint controller for pages relating to creating an account, logging in, logging out, and related
 * functionality.
 */
@RestController
public class LoginController extends APIController {
    @Autowired private VoterListManager voterListManager;

    /** Text template for the body of the email to be sent asking users to confirm their email address. */
    @Value("classpath:static/confirmemail.html")
    protected Resource confirmEmailTemplate;

    @Autowired protected EmailSender emailSender;

    /** File containing the contents of the page sent users when they create a new account but need to confirm email */
    @Value("classpath:static/newawaitconfirm.html")
    protected Resource awaitConfirmPage;

    /** File containing the login page */
    @Value("classpath:static/login.html")
    protected Resource loginPage;

    /** TODO Probably not the best design that we have The Page That Does Everything fed in response to login success */
    @Value("classpath:static/landing.html")
    protected Resource landingPage;

    /** File containing the contents of the page sent users upon logging out */
    @Value("classpath:static/loggedout.html")
    protected Resource loggedOutPage;

    // TODO: we badly need reset password functionality
    @GetMapping("/login")
    public ResponseEntity getLoginPage() throws Exception {
        voterListManager.initialize();
        return ResponseEntity.ok().body(textFromResource(loginPage));
    }

    @GetMapping("/")
    public ResponseEntity landing(@RequestHeader HttpHeaders headers) throws Exception {
        voterListManager.initialize();
        Voter v;
        try {
            v = loginManager.validateBasicUser(headers);
        }
        catch (Exception ex) {
            return getLoginPage();
        }
        return ResponseEntity.ok().body(textFromResource(landingPage));
    }

    @PostMapping("/loginscript")
    public ResponseEntity pleaseLogin(@RequestBody String formData) throws Exception {
        Map<String, String> formValue = parseForm(formData);
        String username = formValue.get("user");
        String password = formValue.get("password");
        if (username == null || password == null) {
            return getLoginPage();
        }
        if (!loginManager.validateLoginCredentials(username, password)) {
            return getLoginPage();
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("Set-Cookie",
                String.format("token=%s; HttpOnly; SameSite=Lax", loginManager.tokenForUser(username)));
        responseHeaders.add("Set-Cookie",
                String.format("user=%s; SameSite=Lax", username));
        /* TODO: the problem with doing it this way is that the URL in the URL bar is loginscript which can't be refreshed. Re-direct would be better. */
        return ResponseEntity.ok().headers(responseHeaders).body(textFromResource(landingPage));
    }

    @PostMapping("/newuser")
    public ResponseEntity newUserForm(@RequestBody String formData) throws Exception {
        Map<String, String> formValue = parseForm(formData);
        // TODO: implement ALL these validations
        // TODO: also duplicate a lot of this checking on the form itself, in JavaScript
        boolean validationChecksOK = true;
        // "user" field mandatory
        if (!formValue.containsKey("user")) { validationChecksOK = false; }
        // user does not contain spaces
        if (formValue.get("user").indexOf(' ') >= 0) { validationChecksOK = false; }
        // Proposed user name not already used by any other account
        Voter existingAccount = voterListManager.getForUsername(formValue.get("user"));
        if (existingAccount != null) {
            // TODO: be much more user-friendly here; return a page w/ this message and with a link back to login.html
            String message = String.format("An account with the username '%s' already exists", formValue.get("user"));
            throw new ForbiddenException();
        }
        // "password" field mandatory
        if (!formValue.containsKey("password")) { validationChecksOK = false; }
        // "password2" field mandatory
        if (!formValue.containsKey("password2")) { validationChecksOK = false; }
        // password == password2
        if (!formValue.get("password").equals(formValue.get("password2"))) { validationChecksOK = false; }
        // "email" field mandatory
        if (!formValue.containsKey("email")) { validationChecksOK = false; }
        // email is a valid email address
        // email not already used by any other account, either in email or oldEmail field
        // "name" field mandatory
        if (!formValue.containsKey("name")) { validationChecksOK = false; }
        if (!validationChecksOK) {
            return getLoginPage();
        }
        Voter v = new Voter(formValue.get("name"), formValue.get("user"), formValue.get("email"));
        if (!voterListManager.addVoter(v, formValue.get("password"))) {
            // This would be a wtf error.
            // TODO: what would be the right way to log ?
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create new account");
        }
        if (!sendConfirmationEmail(v)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Could not send confirmation email");
        }
        String text = new String(Files.readAllBytes(awaitConfirmPage.getFile().toPath()));
        return ResponseEntity.ok().body(text);
    }

    public boolean sendConfirmationEmail(Voter v) {
        try {
            String text = v.processConfirmationEmailText(textFromResource(confirmEmailTemplate));
            // TODO: how do we get the actual URL we're running at?
            text = text.replaceAll("##BASEURL##", "http://localhost:8080");
            emailSender.sendEmail(v.getEmail(), "please confirm your email", text);
            return true;
        }
        catch (Exception ex) {
            ex.printStackTrace();
            // TODO: how to log errors here correctly?
            return false;
        }
    }

    @GetMapping("/confirm")
    public ResponseEntity newUserConfirm(@RequestParam String user, @RequestParam String code) throws Exception {
        if ((user == null) || code == null) {
            return ResponseEntity.badRequest().body("Parameters missing.");
        }
        Voter v = voterListManager.getForUsername(user);
        if (v == null) {
            return ResponseEntity.badRequest().body("Unknown user.");
        }
        if (voterListManager.confirmEmail(v, code)) {
            return getLoginPage();
        }
        else {
            return ResponseEntity.badRequest().body("Invalid confirmation code.");
        }
    }

    @GetMapping("/logout")
    public ResponseEntity doLogout(@RequestHeader HttpHeaders headers) throws IOException {
        Voter v = loginManager.validateBasicUser(headers);
        if (v != null) {
            loginManager.forgetTokenForUser(v);
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("Set-Cookie", "user=");
        responseHeaders.add("Set-Cookie", "token=");
        return ResponseEntity.ok().headers(responseHeaders).body(textFromResource(loggedOutPage));
    }
}
