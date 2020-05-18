package com.jagbag.dvoting;

import com.jagbag.dvoting.entities.Question;
import com.jagbag.dvoting.entities.ResponseOption;
import org.apache.coyote.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResponseOptionTest {

    @Test
    void getText() {
        ResponseOption test = new ResponseOption("yes");
        assertEquals(test.getText(), "yes");
        // test that length of text truncated to 80:
        test = new ResponseOption("012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789 this will be truncated");
        assertEquals(test.getText(), "01234567890123456789012345678901234567890123456789012345678901234567890123456789");
        // test that spaces in response are fine:
        test = new ResponseOption("Barack Hussein Obama");
        assertEquals(test.getText(), "Barack Hussein Obama");
    }

    @Test
    void testEquals() {
        Question q1 = new Question("Are we happy today?");
        ResponseOption r1 = new ResponseOption("yes");
        ResponseOption r2 = new ResponseOption("no");
        q1.addResponseOption(r1);
        q1.addResponseOption(r2);
        assertTrue(r1.equals(r1));
        assertFalse(r1.equals(r2));
        Question q2 = new Question("Should we serve snack at meeting?");
        ResponseOption r3 = new ResponseOption("yes");
        assertFalse(r1.equals(r3));
    }

    @Test
    void testToString() {
        ResponseOption test = new ResponseOption("Barack Hussein Obama");
        assertEquals(test.getText(), test.toString());
    }
}