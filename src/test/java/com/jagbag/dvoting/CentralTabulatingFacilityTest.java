package com.jagbag.dvoting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jagbag.dvoting.entities.Question;
import com.jagbag.dvoting.entities.ResponseOption;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.*;

class CentralTabulatingFacilityTest {

    static CentralTabulatingFacility makeCTFWithQuestion(Question q1) {
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


}