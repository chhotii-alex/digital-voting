package com.jagbag.dvoting.controllers;

import com.jagbag.dvoting.*;
import com.jagbag.dvoting.email.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.persistence.EntityManagerFactory;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Superclass for my various @RestController classes, home of some generally-useful utility methods and
 * commonly-used @Autowired connections.
 *
 * The classes annotated as @RestController (all of whom have names ending in Controller) collectively
 * implement a JSON-based API over HTTP. This looks kinda RESTful; note that, technically, this is not
 * REST, which is intended for semi-public, discoverable, human-readable services. As the back end of
 * our voting system, this service is certainly none of those.
 * (See this brillant column to disabuse yourself of the notion that microservices should be RESTful:
 * https://hackernoon.com/dont-use-rest-for-micro-services-ju7k328m )
 * Thus, no guilt here for the lack of hypermedia HATEOAS links in any of the returned results.
 */
public class APIController {
    // TODO: this should be overridden by an environment variable or command line argument as needed
    @Value( "${base-url}" )
    private String hostBaseURL;

    /** See {@link LoginManager} */
    @Autowired
    protected LoginManager loginManager;
    /** Our connection to the magical Hibernate Java Persistence API implementation. */
    @Autowired
    protected EntityManagerFactory emf;
    /** Connection to the email sending system */
    @Autowired protected EmailSender emailSender;

    public String getHostBaseURL() {
        return hostBaseURL;
    }

    /**
     * Find a file in the classpath and return its text contents.
     * @param myResource a file
     * @return a String containing the file's contents
     * */
    public static String textFromResource(Resource myResource) throws IOException {
        String text = new String(myResource.getInputStream().readAllBytes());
        return text;
    }

    /**
     * Parse a query string, or the data sent by submitting an HTML form, into a Map.
     * Removes URL encoding from the values.
     */
    public Map<String, String> parseForm(String formData) throws UnsupportedEncodingException {
        Map<String, String> formValue = new HashMap<String, String>();
        String[] params = formData.split("&");
        for (String param : params) {
            String[] fields = param.split("=");
            if (fields.length < 2) continue;
            fields[1] = URLDecoder.decode(fields[1], "UTF-8");
            if (fields[1].trim().length() < 1) continue;
            formValue.put(fields[0], fields[1].trim());
        }
        return formValue;
    }

    public ResponseEntity redirectToPage(String pageFileName) {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("Location", pageFileName);
        return ResponseEntity.status(HttpStatus.SEE_OTHER).headers(responseHeaders).build();
    }


    protected ResponseEntity showResponse(String s) {
        s = s + "<a href=\"/\"> Return to main page.</a>";
        return ResponseEntity.ok(s);
    }


}
