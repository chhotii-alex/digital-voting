package com.jagbag.dvoting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;

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
    /** See {@link LoginManager} */
    @Autowired
    protected LoginManager loginManager;
    /** Our connection to the magical Hibernate Java Persistence API implementation. */
    @Autowired
    protected EntityManagerFactory emf;

    /**
     * Find a file in the classpath and return its text contents.
     * @param myResource a file
     * @return a String containing the file's contents
     * */
    protected String textFromResource(Resource myResource) throws IOException {
        File resource = myResource.getFile();
        Path path = resource.toPath();
        String text =  new String(Files.readAllBytes(path));
        return text;
    }

    /**
     * Parse a query string, or the data sent by submitting an HTML form, into a Map.
     * Removes URL encoding from the values.
     */
    protected Map<String, String> parseForm(String formData) throws UnsupportedEncodingException {
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

}
