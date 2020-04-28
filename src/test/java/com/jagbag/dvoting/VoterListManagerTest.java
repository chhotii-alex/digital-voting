package com.jagbag.dvoting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
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