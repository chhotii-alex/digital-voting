package com.jagbag.dvoting;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.*;

class CentralTabulatingFacilityTest {

    CentralTabulatingFacility makeCTFWithQuestion(Question q1) {
        CentralTabulatingFacility ctf = null;
        try {
            ctf = new CentralTabulatingFacility();
        }
        catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            fail("failed to create a CTF");
        }
        try {
            ctf.postQuestion(q1);
        }
        catch (NoSuchAlgorithmException e) {
            fail("NoSuchAlgorithmException thrown");
            e.printStackTrace();
        }
        return ctf;
    }

    @Test
    void votableQuestionList() {
        Question q1 = new Question("What snacks should we have?");
        q1.addResponseOption(new ResponseOption("oatmeal cookies"));
        q1.addResponseOption(new ResponseOption("cranberry orange bread"));
        q1.addResponseOption(new ResponseOption("molasses spice cookies"));
        CentralTabulatingFacility ctf = makeCTFWithQuestion(q1);
        List<Question> list = ctf.votableQuestionList();
        assertEquals(list.size(), 1);
    }

    @Test
    void fillInVotingInfo() {
        String templateText = "##EXPONENT##~##MODULUS##~##QUESTIONS##";
        String questionText = "Is this a simple question?";
        Question q = new Question(questionText);
        q.addResponseOption(new ResponseOption("yes"));
        q.addResponseOption(new ResponseOption("mu"));
        q.addResponseOption(new ResponseOption("no"));
        CentralTabulatingFacility ctf = makeCTFWithQuestion(q);
        String finalText = null;
        try {
            finalText = ctf.fillInVotingInfo(templateText);
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