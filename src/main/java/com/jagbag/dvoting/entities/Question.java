package com.jagbag.dvoting.entities;

import com.jagbag.dvoting.*;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import javax.persistence.*;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.time.*;

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
 * without translation (note: I'd have to do this anyway for internationalization, so that's an argument to
 * TODO: convert the status to an enum).
 * Here are the possible statuses:
 * "new" &mdash; the question has never been put to a vote. Questions start out in the "new" state.
 * While it is new, we allow edits to the text, and to the
 * response options offered to the voters. From this state we may transition to the "polling" state.
 * New questions also may be deleted. (Once a question has been offered for
 * voting, we save it forever for posterity. Or, at least until the database file gets trashed.)
 * "polling" &mdash; the question is being put to a vote. Thus, chits are signed, and votes are accepted and tallied.
 * From this state we may transition to the "closed" state.
 * "closed" &mdash; voting has been ended on the question. We no longer sign any chits nor accept any additonal
 * votes for consideration. We cannot transition to any other state from this state.
 * Status is calculated based on the date/time fields. If postedWhen is null, it's never been posted, and must be
 * new. If postedWhen has a date/time, but closedWhen is null, it's polling. If closedWhen has a date/time, it's
 * closed.
 * A Question has a CountingType, which governs how many responses are accepted from each Voter and
 * how they are counted.
 * CountingType.SINGLE means that each Voter may only submit one response.
 * TODO: CountingType.MULTIPLE means that each Voter may submit one or more response, not ranked (low-priority to implement but easy?)
 * CountingType.RANKED_CHOICE means that a Voter submits multiple choices, ranked by preference.
 * The server end just remembers the CountingType of each Question, and enforces that the number of responses
 * received does not exceed what is appropriate for the type (only one for SINGLE; as many as there are options
 * for RANKED_CHOICE), and remembers the ranking, if relevant, of each vote. All the real action for handling
 * RANKED_CHOICE voting differently has to happen in the client side: allowing the user to order their
 * choices at the voting end, and going through the ranked choice algorithm to pick a winner on the reporting
 * end.
 */
@Entity
public class Question extends SigningEntity {
    public enum CountingType {
        SINGLE,
        MULTIPLE,
        RANKED_CHOICE
    }
    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private long id;
    private String text;
    CountingType type;
    private java.time.LocalDateTime createdWhen;
    private java.time.LocalDateTime postedWhen;
    private java.time.LocalDateTime closedWhen;
    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_question")
    public List<ResponseOption> possibleResponses;
    @Transient
    protected HashMap<String, String> blindedChitByUser;

    protected Question() {} // Hibernate needs this

    public Question(String text) {
        this.text = text;
        type = CountingType.SINGLE;
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
    public CountingType getType() { return type; }
    public void setType(CountingType type) { this.type = type; }

    public List<ResponseOption> getPossibleResponses() { return possibleResponses; }
    public int numberOfPossibleResponses() { return possibleResponses.size(); }
    public int numberOfAllowedChits() {
        return numberOfPossibleResponses();
    }

    public boolean acceptsResponseRank(int ranking) {
        int max = 0;
        switch(type) {
            case SINGLE:
                max = 1;
                break;
            case MULTIPLE:
            case RANKED_CHOICE:
                max = numberOfPossibleResponses();
                break;
        }
        return (ranking < max);
    }

    public void setCreateDateTime() {
        if (type == null) {
            type = CountingType.SINGLE;
        }
        createdWhen = java.time.LocalDateTime.now();
    }
    public java.time.LocalDateTime getCreatedWhen() { return createdWhen; }
    public void post() throws NoSuchAlgorithmException {
        initializeKeys();
        blindedChitByUser = new HashMap<String, String>();
        postedWhen = java.time.LocalDateTime.now();
    }
    public void close() {
        closedWhen = LocalDateTime.now();
    }
    public LocalDateTime getClosedWhen() { return closedWhen; }

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

    /**
     * Get the modulus for the RSA key pair used for signing me chits for sending to the client via JSON.
     */
    @JsonGetter("modulusStr")
    public String getModulusString() {
        if (rsaKeys == null) return null;
        return getPublicKey().getModulus().toString(10);
    }

    /**
     * Get the public key of the RSA key pair used for signing me chits for sending to the client via JSON.
     * The public key and modulus are sent to the client, and can be shouted from the rooftops.
     * Note the lack of any public method exposing the private key, however.
     */
    @JsonGetter("exponentStr")
    public String getPublicExponentString() {
        if (rsaKeys == null) return null;
        return getPublicKey().getPublicExponent().toString(10);
    }

    public String getBlindedChitForUser(String username) {
        return blindedChitByUser.get(username);
    }

    public void setBlindedChitForUser(String username, String blindedMessageText) {
        blindedChitByUser.put(username, blindedMessageText);
    }
}
