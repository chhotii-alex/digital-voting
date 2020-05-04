package com.jagbag.dvoting;

import org.junit.jupiter.api.Test;

import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class QuestionTest {

    @Test
    void setText() {
        Question q1 = new Question("");
        String str = "Is the laundry done?";
        q1.setText(str);
        assertEquals(str, q1.getText());
    }

    @Test
    void addResponseOption() {
        Question q1 = new Question("Who is the most awesome?");
        q1.addResponseOption(new ResponseOption("Barack Obama"));
        q1.addResponseOption(new ResponseOption("Anthony Cuomo"));
        q1.addResponseOption(new ResponseOption("Elizabeth Warren"));
        assertEquals(q1.getPossibleResponses().size(), 3);
    }

    @Test
    void clearResponseOptions() {
        String item = "Cheetos";
        Question q1 = new Question("What snacks should we have?");
        q1.addResponseOption(new ResponseOption(item));
        boolean found = false;
        for (ResponseOption r : q1.getPossibleResponses()) {
            if (r.getText().equals(item)) { found = true; }
        }
        assertTrue(found);
        q1.clearResponseOptions();
        q1.addResponseOption(new ResponseOption("pumpkin seeds"));
        q1.addResponseOption(new ResponseOption("pita chips"));
        found = false;
        for (ResponseOption r : q1.getPossibleResponses()) {
            if (r.getText().equals(item)) { found = true; }
        }
        assertFalse(found);
    }

    @Test
    void numberOfPossibleResponses() {
        Question q1 = new Question("What snacks should we have?");
        assertEquals(q1.numberOfPossibleResponses(), 0);
        q1.addResponseOption(new ResponseOption("oatmeal cookies"));
        assertEquals(q1.numberOfPossibleResponses(), 1);
        q1.addResponseOption(new ResponseOption("cranberry orange bread"));
        assertEquals(q1.numberOfPossibleResponses(), 2);
        q1.addResponseOption(new ResponseOption("molasses spice cookies"));
        assertEquals(q1.numberOfPossibleResponses(), 3);
    }

    @Test
    void numberOfAllowedChits() {
        Question q1 = new Question("What snacks should we have?");
        assertEquals(q1.numberOfAllowedChits(), 1);
        q1.addResponseOption(new ResponseOption("oatmeal cookies"));
        assertEquals(q1.numberOfAllowedChits(), 2);
        q1.addResponseOption(new ResponseOption("cranberry orange bread"));
        assertEquals(q1.numberOfAllowedChits(), 3);
        q1.addResponseOption(new ResponseOption("molasses spice cookies"));
        assertEquals(q1.numberOfAllowedChits(), 4);
    }

    @Test
    void setCreateDateTime() {
        Question q1 = new Question("What snacks should we have?");
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        q1.setCreateDateTime();
        java.time.LocalDateTime createTime = q1.getCreatedWhen();
        long seconds = ChronoUnit.SECONDS.between(now, createTime);
        assertEquals(0, seconds);
    }

    @Test
    void post() {
        Question q1 = new Question("What snacks should we have?");
        assertTrue(q1.canEdit());
        assertTrue(q1.canDelete());
        assertTrue(q1.canPost());
        assertFalse(q1.canClose());
        assertEquals(q1.getStatus(), "new");
        try {
            q1.post();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            fail("posting threw");
        }
        assertFalse(q1.canEdit());
        assertFalse(q1.canDelete());
        assertFalse(q1.canPost());
        assertTrue(q1.canClose());
        assertEquals(q1.getStatus(), "polling");
        q1.close();
        assertFalse(q1.canEdit());
        assertFalse(q1.canDelete());
        assertFalse(q1.canPost());
        assertFalse(q1.canClose());
        assertEquals(q1.getStatus(), "closed");
    }
}