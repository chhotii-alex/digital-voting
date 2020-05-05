package com.jagbag.dvoting;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JacksonJsonParser;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import net.minidev.json.JSONObject;


import java.util.ArrayList;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class QuestionAdminControllerTest {
    @Autowired private QuestionAdminController questionAdminController;
    @Autowired
    private VoterListManager voterListManager;
    @Autowired
    protected LoginManager loginManager;
    @Autowired
    private CentralTabulatingFacility ctf;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    public HttpHeaders setUpAndLogIn() {
        // Test suite will run with a brand-new empty database.
        // All the methods we're testing demand authorization as an admin user.
        // Have the admin user created so we can log in as that.
        try {
            voterListManager.initialize();
        }
        catch (Exception e) {
            e.printStackTrace();
            fail("weird exception thrown");
        }
        // log in as admin
        if (!loginManager.validateLoginCredentials("admin", "changeme!")) {
            fail("Could not log in as admin");
        }
        String token = loginManager.tokenForUser("admin");
        HttpHeaders headers = new HttpHeaders();
        ArrayList<String> cookies = new ArrayList<String>();
        cookies.add(String.format("user=%s;token=%s", "admin", token));
        headers.put(HttpHeaders.COOKIE, cookies);
        return headers;
    }

    protected Integer freshQuestionId(Question q1, HttpHeaders headers) {
        try {
            ResultActions ra = mockMvc.perform(post("/questions")
                    .headers(headers)
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(q1)))
                    .andExpect(status().isCreated());
            MvcResult result = ra.andReturn();
            String returnedData = result.getResponse().getContentAsString();
            System.out.println(returnedData);
            JsonParser parser = new JacksonJsonParser();
            Map<String,Object> map = parser.parseMap(returnedData);
            Integer quid = (Integer)(map.get("id"));
            return quid;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Test
    void addQuestion() {
        Question q1 = new Question("What snacks should we have?");
        q1.addResponseOption(new ResponseOption("oatmeal cookies"));
        q1.addResponseOption(new ResponseOption("cranberry orange bread"));
        q1.addResponseOption(new ResponseOption("molasses spice cookies"));
        HttpHeaders headers = setUpAndLogIn();
        Integer quid = freshQuestionId(q1, headers);
        assertNotNull(quid);
        Question savedQuestion = ctf.lookUpQuestion(quid);
        assertEquals(savedQuestion.getText(), q1.getText());
        assertEquals(savedQuestion.numberOfPossibleResponses(), 3);
    }

    @Test
    void patchQuestion() {
        Question q1 = new Question("What text should this question have?");
        q1.addResponseOption(new ResponseOption("yes"));
        q1.addResponseOption(new ResponseOption("no"));
        q1.addResponseOption(new ResponseOption("abstain"));
        HttpHeaders headers = setUpAndLogIn();
        Integer quid = freshQuestionId(q1, headers);

        String newQuestionText = "Should we serve snack at meeting?";
        Question q2 = new Question(newQuestionText);
        q2.addResponseOption(new ResponseOption("yes"));
        q2.addResponseOption(new ResponseOption("no"));
        q2.addResponseOption(new ResponseOption("mu"));
        q2.addResponseOption(new ResponseOption("abstain"));

        String url = String.format("/questions/%d", quid);
        String jsonString = null;
        try {
            jsonString = objectMapper.writeValueAsString(q2);
        } catch (Exception e) {
            e.printStackTrace();
            fail("could not make JSON for request");
        }
        try {
            ResultActions ra = mockMvc.perform(patch(url)
                        .headers(headers)
                        .contentType("application/json")
                        .content(jsonString))
                        .andExpect(status().isOk());
            Question savedQuestion = ctf.lookUpQuestion(quid);
            assertEquals(savedQuestion.getText(), newQuestionText);
            assertEquals(savedQuestion.numberOfPossibleResponses(), 4);
        } catch (Exception e) {
            e.printStackTrace();
            fail("PATCH threw");
        }
    }

    @Test
    void postAndCloseQuestion() {
        Question q1 = new Question("What snacks should we have?");
        q1.addResponseOption(new ResponseOption("oatmeal cookies"));
        q1.addResponseOption(new ResponseOption("cranberry orange bread"));
        q1.addResponseOption(new ResponseOption("molasses spice cookies"));
        HttpHeaders headers = setUpAndLogIn();
        Integer quid = freshQuestionId(q1, headers);
        Question savedQuestion = ctf.lookUpQuestion(quid);
        assertEquals(savedQuestion.getStatus(), "new");
        try {
            ResultActions ra = mockMvc.perform(patch("/questions/{quid}/post", quid)
                    .headers(headers) )
                    .andExpect(status().isOk());
        } catch (Exception e) {
            e.printStackTrace();
            fail("PATCH threw when posting");
        }
        savedQuestion = ctf.lookUpQuestion(quid);
        assertEquals(savedQuestion.getStatus(), "polling");
        try {
            ResultActions ra = mockMvc.perform(patch("/questions/{quid}/close", quid)
                    .headers(headers) )
                    .andExpect(status().isOk());
        } catch (Exception e) {
            e.printStackTrace();
            fail("PATCH threw");
        }
        savedQuestion = ctf.lookUpQuestion(quid);
        assertEquals(savedQuestion.getStatus(), "closed");
    }

    @Test
    void deleteQuestion() {
    }
}