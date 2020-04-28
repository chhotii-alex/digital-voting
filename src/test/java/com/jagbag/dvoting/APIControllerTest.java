package com.jagbag.dvoting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class APIControllerTest {

    @Test
    void textFromResource() {
        APIController test = new APIController();
        try {
            ResourceLoader resourceLoader = new DefaultResourceLoader();
            Resource resource = resourceLoader.getResource("file:src/test/resources/static/trivial.html");
            String result = test.textFromResource(resource);
            String expected = String.join("\n",
                    "<!DOCTYPE html>",
                    "<html lang=\"en\">",
                    "<head>",
                    "    <meta charset=\"UTF-8\">",
                    "    <title>Trivial Page</title>",
                    "</head>",
                    "<body>",
                    "This page has barely any content.",
                    "</body>",
                    "</html>");
            assertEquals(result, expected);
        } catch (IOException e) {
            fail("textFromResource threw " + e);
        }
    }

    @Test
    void parseForm() {
        String testData = "user=alex&password=password&password2=password&email=alex%40jagbag.com&name=Alex+Morgan";
        APIController test = new APIController();
        try {
            Map<String, String> map = test.parseForm(testData);
            assertEquals(map.get("user"), "alex");
            assertEquals(map.get("email"), "alex@jagbag.com");
            assertEquals(map.get("name"), "Alex Morgan");
        } catch (UnsupportedEncodingException e) {
            fail("parseForm threw " + e);
        }
    }
}