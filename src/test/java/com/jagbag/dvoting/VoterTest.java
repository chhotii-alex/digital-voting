package com.jagbag.dvoting;

import com.jagbag.dvoting.entities.Voter;
import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class VoterTest {

    @Test
    void setPassword() {
        Voter v = new Voter("Alex M", "alexm", "alex@jagbag.com");
        String pwd = "hugabugga";
        try {
            v.setPassword(pwd);
        }
        catch (Exception e) {
            e.printStackTrace();
            fail("set password threw: " + e);
        }
        try {
            assertFalse(v.checkPassword("notmypassword"));
            assertTrue(v.checkPassword(pwd));
        }
        catch (Exception e) {
            e.printStackTrace();
            fail("check password threw: " + e);
        }
    }

    @Test
    void prepareForConfirmationEmail() {
        Voter v = new Voter("Alex M", "alexm", "alex@jagbag.com");
        v.prepareForConfirmationEmail();
    }

    @Test
    void canConfirmCode() {
        Voter v = new Voter("Alex M", "alexm", "alex@jagbag.com");
        assertFalse(v.isActiveAccount());
        v.prepareForConfirmationEmail();
        String emailText = null;
        try {
            emailText = v.processEmailText("##CODE##");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            fail("UnsupportedEncodingException");
        }
        v.confirmEmail("rAnDOMwrongC0D3");
        assertFalse(v.isActiveAccount());
        assertFalse(v.isEmailConfirmed());
        v.confirmEmail(emailText);
        assertTrue(v.isEmailConfirmed());
        assertTrue(v.isActiveAccount());
    }

    @Test
    void processConfirmationEmailText() {
        Voter v = new Voter("David", "squiggles", "david@nowhere.org");
        String emailText = null;
        try {
            emailText = v.processEmailText("##NAME##");
            assertEquals(emailText, v.getName());
            emailText = v.processEmailText("##USERNAME##");
            assertEquals(emailText, v.getUsername());
        } catch (UnsupportedEncodingException e) {
            fail("UnsupportedEncodingException");
            e.printStackTrace();
        }
    }

    @Test
    void getCurrentEmail() {
        Voter v = new Voter("Zoe M", "zoe", "zzzzz@jagbag.com");
        assertNull(v.getCurrentEmail());
        assertFalse(v.isActiveAccount());
        v.prepareForConfirmationEmail();
        String emailText = null;
        try {
            emailText = v.processEmailText("##CODE##");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            fail("UnsupportedEncodingException");
        }
        v.confirmEmail(emailText);
        assertNotNull(v.getCurrentEmail());
        assertTrue(v.isActiveAccount());
    }
}