package com.jagbag.dvoting;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

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

    @Value( "${base-url}" )
    private String hostBaseURL;

    @Value( "${auto-priv-everyone:no}" )
    private String autoPrivilegeEveryone;
    public boolean isAutoPrivilegingEveryone() {
        return autoPrivilegeEveryone.equals("yes");
    }

    /** Text template for the body of the email to be sent asking users to confirm their email address. */
    @Value("classpath:static/confirmemail.html")
    protected Resource confirmEmailTemplate;

    /** File containing the contents of the page sent users when they create a new account but need to confirm email */
    @Value("classpath:static/newawaitconfirm.html")
    protected Resource awaitConfirmPage;

    /** Text template for the body of the reset password email. */
    @Value("classpath:static/reset.html")
    protected Resource resetEmailTemplate;

    /** File containing the login page */
    @Value("classpath:static/login.html")
    protected Resource loginPage;

    /**File containing page we return from new user form if new user proposed username not unique */
    @Value("classpath:static/dupnameerr.html")
    protected Resource duplicateNamePage;

    /** File continaing the page with the 'forgot password' form */
    @Value("classpath:static/forgot.html")
    protected Resource forgotPasswordPage;

    /** File continaing the page responding to the  the 'forgot password' form */
    @Value("classpath:static/checkemail.html")
    protected Resource checkEmailPage;

    /** Landing page, fed in response to login success. Links to Voter and Admin pages as needed. */
    @Value("classpath:static/landing.html")
    protected Resource landingPage;

    /** File containing the contents of the page sent users upon logging out */
    @Value("classpath:static/loggedout.html")
    protected Resource loggedOutPage;

    /** Page announcing success **/
    @Value("classpath:static/success.html")
    protected Resource successPage;

    // Log-in Page
    @GetMapping("/login")
    public ResponseEntity getLoginPage() throws Exception {
        return ResponseEntity.ok().body(textFromResource(loginPage));
    }

    /**
     * Endpoint for the "Forgot Password" button. Just takes user to a page where they can start the reset process.
     * @return a page to the user with the plssendreset link
     */
    @GetMapping("/forgot")
    public ResponseEntity getForgotPasswordPage()  {
        if (emailSender.isConfiguredForEmail() ){
            try {
                return ResponseEntity.ok().body(textFromResource(forgotPasswordPage));
            } catch (IOException e) {
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Could not find reset password page");
            }
        }
        else {
            return ResponseEntity.ok().body("I'm sorry, password resetting is not currently available. Please contact Alex for help.");
        }
    }

    /**
     * Handle a request for a password reset from a user.
     * When a user clicks on "Forgot Password" they are brought to a form with input for their username and an
     * email address, which submits to this endpoint. We try to look up the user by username and/or email address.
     * If they are found, @see com.jagbag.dvoting.VotingListMaganger#prepareForReset() for how things are handled
     * from there; and the user is sent to a page telling them to expect email.
     * @param formData
     * @return
     * @throws Exception
     */
    @PostMapping("/plssendreset")
    public ResponseEntity requestPasswordReset(@RequestBody String formData) throws Exception {
        Map<String, String> formValue = parseForm(formData);
        String username = formValue.get("user");
        String email = formValue.get("email");
        if (username == null && email == null) {
            return getLoginPage();
        }
        Voter v = null;
        if (username != null) {
            v = voterListManager.getForUsername(username);
        }
        if (v == null && email != null) {
            v = voterListManager.getForEmail(email);
        }
        if (v == null) {
            String message = "I'm sorry, no user account with ";
            if (username != null) {
                message = message + String.format("the user name '%s' ", username );
            }
            if (username != null && email != null) {
                message = message + "or ";
            }
            if (email != null) {
                message = message + String.format("the email address '%s' ", email);
            }
            message = message + "exists.";
            return ResponseEntity.ok(message);
        }
        if (voterListManager.prepareForReset(v)) {
            sendResetEmail(v);
            return ResponseEntity.ok().body(textFromResource(checkEmailPage));
        }
        else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Could not prepare for reset");
        }
    }

    /**
     * Endpoint for actually finalizing a password reset.
     * @param user
     * @param code
     * @return the login page to the user
     * @throws Exception
     */
    @GetMapping("/reset")
    public ResponseEntity receiveResetRequest(@RequestParam String user, @RequestParam String code) throws Exception {
        Voter v = voterListManager.getForUsername(user);
        if (v != null) {
            voterListManager.resetPassword(v, code);
        }
        return getLoginPage();
    }


    @GetMapping("/")
    public ResponseEntity landing(@RequestHeader HttpHeaders headers) throws Exception {
        Voter v;
        try {
            v = loginManager.validateBasicUser(headers);
        }
        catch (Exception ex) {
            return getLoginPage();
        }
        return ResponseEntity.ok().body(textFromResource(landingPage));
    }

    @GetMapping("/landing")
    public ResponseEntity getLanding(@RequestHeader HttpHeaders headers) throws Exception {
        return landing(headers);
    }

    @PostMapping("/landing")
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
        responseHeaders.add("Location", "/");
        return ResponseEntity.status(HttpStatus.SEE_OTHER).headers(responseHeaders).build();
    }

    @PostMapping("/changepassword")
    public ResponseEntity changePassword(@RequestHeader HttpHeaders headers, @RequestBody String formData) throws Exception {
        Voter v = loginManager.validateBasicUser(headers);
        Map<String, String> formValue = parseForm(formData);
        String oldPassword = formValue.get("password");
        String newPassword1 = formValue.get("password1");
        String newPassword2 = formValue.get("password2");
        if (oldPassword == null || newPassword1 == null ||  newPassword2 == null) {
            return getLanding(headers);
        }
        if (!newPassword1.equals(newPassword2)) {
            return getLanding(headers);
        }
        if (!loginManager.validateLoginCredentials(v.getUsername(), oldPassword)) {
            return getLanding(headers);
        }
        // validateLoginCredentials will have re-set the token, so they will have to log in again
        if (!voterListManager.setPassword(v, newPassword1)) {
            // this would be wtf
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to change password");
        }
        return ResponseEntity.ok().body(textFromResource(successPage));
    }

    @PostMapping("/newuser")
    public ResponseEntity newUserForm(@RequestBody String formData) throws Exception {
        Map<String, String> formValue = parseForm(formData);
        // This duplicates a lot of this checking on the form itself, in JavaScript
        boolean validationChecksOK = true;
        // "user" field mandatory
        if (!formValue.containsKey("user")) { validationChecksOK = false; }
        // user does not contain spaces
        if (formValue.get("user").indexOf(' ') >= 0) { validationChecksOK = false; }
        // Proposed user name not already used by any other account
        Voter existingAccount = voterListManager.getForUsername(formValue.get("user"));
        if (existingAccount != null) { // Inform user that there's an existing account with that username
            String text = textFromResource(duplicateNamePage);
            text = text.replaceAll("##USER##", formValue.get("user"));
            return ResponseEntity.ok().body(text);
        }
        // "password" field mandatory
        if (!formValue.containsKey("password")) { validationChecksOK = false; }
        // "password2" field mandatory
        if (!formValue.containsKey("password2")) { validationChecksOK = false; }
        // password == password2
        if (!formValue.get("password").equals(formValue.get("password2"))) { validationChecksOK = false; }
        // "email" field mandatory
        if (!formValue.containsKey("email")) { validationChecksOK = false; }
        // TODO: validate that email is a valid email address
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
        if (isAutoPrivilegingEveryone()) {  // FOR DEMO ONLY!!!
            v.setAllowedToVote(true);
            v.setAdmin(true);
            voterListManager.updateVoter(v);
        }
        if (emailSender.isConfiguredForEmail()) {
            if (!sendConfirmationEmail(v)) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Could not send confirmation email");
            }
            String text = new String(Files.readAllBytes(awaitConfirmPage.getFile().toPath()));
            return ResponseEntity.ok().body(text);
        }
        else {
            // If email is not available, turn off requiring confirmation of email. Activate immediately.
            voterListManager.activateAccountWithoutConfirm(v);
            return ResponseEntity.ok().body("Created account! Click back button to return to login page.");
        }
    }

    private boolean sendConfirmationEmail(Voter v) {
        try {
            String text = v.processEmailText(textFromResource(confirmEmailTemplate));
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

    /**
     * Send the email to the user that allows them to reset their password.
     * @param v - person whose password is being reset
     * @return
     */
    protected boolean sendResetEmail(Voter v) {
        try {
            String text = v.processEmailText(textFromResource(resetEmailTemplate));
            // TODO: how do we get the actual URL we're running at?
            text = text.replaceAll("##BASEURL##", "http://localhost:8080");
            emailSender.sendEmail(v.getEmail(), "your password reset", text);
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
            return ResponseEntity.ok().body(textFromResource(successPage));
        }
        else {
            return ResponseEntity.badRequest().body("Invalid confirmation code.");
        }
    }

    @GetMapping("/logout")
    public ResponseEntity doLogout(@RequestHeader HttpHeaders headers) throws IOException {
        Voter v = null;
        try {
            v = loginManager.validateBasicUser(headers);
        }
        catch (Exception ex) {}
        if (v != null) {
            loginManager.forgetTokenForUser(v);
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("Set-Cookie", "user=");
        responseHeaders.add("Set-Cookie", "token=");
        return ResponseEntity.ok().headers(responseHeaders).body(textFromResource(loggedOutPage));
    }
}
