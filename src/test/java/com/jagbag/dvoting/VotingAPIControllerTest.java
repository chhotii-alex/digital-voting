package com.jagbag.dvoting;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class VotingAPIControllerTest {
    @Autowired private VotingAPIController votingAPIController;
    @Autowired
    private CentralTabulatingFacility ctf;

    //TODO see https://spring.io/guides/gs/testing-web/ for how to implement such tests
    @Test
    void getOpenQuestions() {
    }

    @Test
    void getChitSigned() {
    }

    @Test
    void submitVote() {
    }

    @Test
    void getVoteVerificationInfo() {
    }

    @Test
    void fillInVotingInfo() {
        String templateText = "##EXPONENT##~##MODULUS##~##QUESTIONS##";
        Voter v = new Voter("Someone", "someone", "nobody@nowhere.com");
        String questionText = "Is this a simple question?";
        Question q = new Question(questionText);
        q.addResponseOption(new ResponseOption("yes"));
        q.addResponseOption(new ResponseOption("mu"));
        q.addResponseOption(new ResponseOption("no"));
        try {
            ctf.postQuestion(q);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            fail("NoSuchAlgorithmException");
        }
        String finalText = null;
        try {
            finalText = votingAPIController.fillInVotingInfo(templateText, v);
        }
        catch (JsonProcessingException e) {
            e.printStackTrace();
            fail("JSON serialization error");
        }
        String[] fields = finalText.split("~");
        assertEquals(fields[0], ctf.getPublicExponent().toString(10));
        assertEquals(fields[1], ctf.getModulus().toString(10));
        assertTrue(finalText.contains("~"));
        assertFalse(finalText.contains("purple cow"));
        assertTrue(fields[2].contains(questionText));
    }
}