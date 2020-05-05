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
    void getForUsername() {
    }

    @Test
    void voters() {
    }

    @Test
    void addVoter() {
    }

    @Test
    void removeVoter() {
    }

    @Test
    void confirmEmail() {
    }
}