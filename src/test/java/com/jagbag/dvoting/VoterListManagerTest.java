package com.jagbag.dvoting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class VoterListManagerTest {

    @Autowired
    private VoterListManager voterListManager;

    /*
    TODO: use separate set(s) of app properties; point to a test database; init db to a known state; then write these tests
     */

    @Test
    void initialize() {
        try {
            voterListManager.initialize();
            // This set of tests runs off a test database that is fresh each time.
            // So initialize() should create the admin user.
            Voter v = voterListManager.getForUsername("admin");
            if (v == null) {
                fail("Initialize didn't create admin user");
            }
            if (!v.isAdmin()) {
                fail("Didn't create a known usr with admin priv");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            fail("initialize threw: " + e);
        }
    }

    @Test
    void addVoter() {
        String email = "alice@xyz.com";
        String name = "Alice";
        String username = "alice";
        Voter v = new Voter(name, username, email);
        voterListManager.addVoter(v, "!@#$%^&*(");
        Voter v2 = voterListManager.getForEmail(email);
        assertNotNull(v2);
        assertEquals(name, v2.getName());
        assertEquals(username, v2.getUsername());
        assertFalse(v2.isActiveAccount());
    }

    @Test
    void removeVoter() {
        String email = "bob@xyz.com";
        String name = "Bob";
        String username = "bob";
        Voter v = new Voter(name, username, email);
        voterListManager.addVoter(v, "!@#$%^&*(");
        assertNotNull(voterListManager.getForUsername("bob"));
        voterListManager.removeVoter(v);
        assertNull(voterListManager.getForUsername("bob"));
    }

    @Test
    void confirmEmail() {
        String email = "carol@xyz.com";
        String name = "Carol";
        String username = "carol";
        assertNull(voterListManager.getForUsername(username));
        Voter v = new Voter(name, username, email);
        voterListManager.addVoter(v, "!@#$%^&*(");
        v = voterListManager.getForUsername(username);
        assertFalse(v.isActiveAccount());
        String phonyEmailTemplate = "##USERNAME##:message:##CODE##";
        String phonyEmailText = v.processEmailText(phonyEmailTemplate);
        String[] fields = phonyEmailText.split(":");
        String confirmationCode = fields[2];
        assertEquals(fields[0], username);
        voterListManager.confirmEmail(v, confirmationCode);
        v = voterListManager.getForUsername(username);
        assertTrue(v.isActiveAccount());
    }

    @Test
    void getForUsername() {
    }

    @Test
    void voters() {
    }

}