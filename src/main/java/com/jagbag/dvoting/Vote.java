package com.jagbag.dvoting;

import org.hibernate.annotations.Immutable;
import javax.persistence.*;

/**
 * An instance of the Vote class represents a vote submitted by a voter on a question.
 * We do not know what Vote came from what Voter. The evidence that we have that this is
 * valid vote (from an authorized Voter) is that each Vote comes with a signed version of
 * that vote, which is vetted by the CTF.
 * We do have a numeric identifier. Voters (actually, the JavaScript running in their browsers)
 * know the numeric identifiers that they assigned their votes, and thus can verify that their
 * votes was counted by observing that that numeric identifier appears in the list of votes in the
 * tabulation.
 */
@Entity
@Immutable
public class Vote {
    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private long id;
    @ManyToOne
    @JoinColumn(name = "fk_question")
    private Question question;
    private String response;
    private java.time.LocalDateTime receivedWhen;
    private String voterChitNumber;
    private String responseChitNumber;

    protected Vote() {} // Hibernate needs this
    public Vote(Question question, String response, String voterChitNumber, String responseChitNumber) {
        this.question = question;
        this.response = response;
        this.voterChitNumber = voterChitNumber;
        this.responseChitNumber = responseChitNumber;
        this.receivedWhen = java.time.LocalDateTime.now();
    }

    public long getId() { return id; }
    public Question getQuestion() { return question; }
    public String getResponse() { return response; }
    public java.time.LocalDateTime getReceivedWhen() { return receivedWhen; }
    public String getVoterChitNumber() { return voterChitNumber; }
    public String getResponseChitNumber() { return responseChitNumber; }
}
