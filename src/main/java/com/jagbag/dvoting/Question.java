package com.jagbag.dvoting;

import com.fasterxml.jackson.annotation.JsonGetter;

import javax.persistence.*;
import java.util.*;

/**
 * A question that can be put to vote. A question has text, and a list of options from which voters may choose.
 * Each question has a unique numeric id (assigned by Hibernate/the database).
 * For example:
 * 1. Shall we serve snacks at meeting?
 * - yes
 * - no
 * - abstain
 * 2. Who shall be the interim president?
 * - Nick
 * - Rick
 * - Rachel
 * - none of the above
 * - abstain
 * A Question has a status, which I return as a String, not an enum, so that it can be displayed in the client
 * without translation (note: I'd have to do this anyway for internationalization, so that's an argument to converting
 * the status to an enum). Here are the possible statuses:
 * "new" &mdash; the question has never been put to a vote. Questions start out in the "new" state.
 * While it is new, we allow edits to the text, and to the
 * response options offered to the voters. From this state we may transition to the "polling" state.
 * New questions also may be deleted. (Once a question has been offered for
 * voting, we save it forever for posterity.)
 * "polling" &mdash; the question is being put to a vote. Thus, chits are signed, and votes are accepted and tallied.
 * From this state we may transition to the "closed" state.
 * "closed" &mdash; voting has been ended on the question. We no longer sign any chits nor accept any additonal
 * votes for consideration. We cannot transition to any other state from this state.
 * Status is calculated based on the date/time fields. If postedWhen is null, it's never been posted, and must be
 * new. If postedWhen has a date/time, but closedWhen is null, it's polling. If closedWhen has a date/time, it's
 * closed.
 */
@Entity
public class Question {
    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private long id;
    private String text;
    private java.time.LocalDateTime createdWhen;
    private java.time.LocalDateTime postedWhen;
    private java.time.LocalDateTime closedWhen;
    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_question")
    private List<ResponseOption> possibleResponses;

    protected Question() {} // Hibernate needs this

    public Question(String text) {
        this.text = text;
        possibleResponses = new ArrayList<ResponseOption>();
    }
    public void clearResponseOptions() {
        possibleResponses = new ArrayList<ResponseOption>();
    }
    public void addResponseOption(ResponseOption newOption) {
        possibleResponses.add(newOption);
        newOption.setQuestion(this);
    }
    public long getId() { return id; }
    public String getText() { return text; }
    public void setText(String newText) { text = newText; }

    public List<ResponseOption> getPossibleResponses() { return possibleResponses; }
    public int numberOfPossibleResponses() { return possibleResponses.size(); }
    public int numberOfAllowedChits() {
        return numberOfPossibleResponses()+1;
    }

    protected void setCreateDateTime() {
        createdWhen = java.time.LocalDateTime.now();
    }
    public java.time.LocalDateTime getCreatedWhen() { return createdWhen; }
    public void post() {
        postedWhen = java.time.LocalDateTime.now();
    }
    public void close() {
        closedWhen = java.time.LocalDateTime.now();
    }

    @JsonGetter("editable")
    public boolean canEdit() {
        return (postedWhen == null);
    }
    @JsonGetter("deletable")
    public boolean canDelete() {
        return (postedWhen == null);
    }
    @JsonGetter("postable")
    public boolean canPost() {
        return (postedWhen == null);
    }
    @JsonGetter("closable")
    public boolean canClose() {
        return (postedWhen != null && closedWhen == null);
    }
    @JsonGetter("status")
    public String getStatus() {
        if (closedWhen != null) {
            return "closed";
        }
        else if (postedWhen != null) {
            return "polling";
        }
        else {
            return "new";
        }
    }
}
