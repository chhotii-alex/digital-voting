package com.jagbag.dvoting;

/**
 * Encapsulate a string in an object. For use as a parameter of a @PostMapping endpoint, when the client
 * sends just a string. Spring really really wants to package what you POST into an object, I guess, so here
 * you go&mdash; an object.
 */
public class PostPayload {
    protected PostPayload() {}
    private String b;
    public String getB() { return b; }
}
