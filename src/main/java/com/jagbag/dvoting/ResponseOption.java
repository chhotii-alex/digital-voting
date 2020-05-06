package com.jagbag.dvoting;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;

/**
 * An object of type ResponseOption represents one out of a set of possible responses that a voter may vote
 * for on a Question.
 * Each Question has its own non-overlapping set of ResponseOptions. So, for example, if two questions both
 * allow a response with the text "yes", each of those yesses is represented by a separate object.
 * We must limit the length of the ResponseOption text. We are using a block cipher algorithm to sign chits;
 * however, I go on the assumption that all messages we need to sign are smaller than one block in length, and
 * thus I don't actually handle dividing messages into blocks. To keep this being true&mdash; a length limit of
 * 80 empirically works, and shouldn't be onerous.
 */
@Entity
public class ResponseOption {
    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private long id;
    private String text;
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "fk_question")
    private Question question;

    static final int MAX_LENGTH = 80;

    private ResponseOption() {}

    public ResponseOption(String text) {
        if (text.length() > MAX_LENGTH) {
            text = text.substring(0, MAX_LENGTH);
        }
        this.text = text;
    }

    public String getText() {
        return text;
    }
    public void setText(String newText) { text = newText; }

    public boolean equals(Object obj) {
        if (!obj.getClass().equals(this.getClass())) return false;
        if (!obj.toString().equals(this.toString())) return false;
        if (((ResponseOption)obj).question != this.question) return false;
        return true;
    }

    public String toString() {
        return text;
    }

    public int hashCode() {
        return text.hashCode();
    }

    public void setQuestion(Question question) {
        this.question = question;
    }
}
