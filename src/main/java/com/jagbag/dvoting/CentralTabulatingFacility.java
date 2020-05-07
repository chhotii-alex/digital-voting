package com.jagbag.dvoting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import java.util.*;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO: ranked choice voting is a REQUIREMENT
/**
 * Implements the server end of the blind voting protocol.
 *
 * See Applied Cryptography: Protocols, Algorithms, and Source Code in C by Bruce Schneier,
 * (c) 1994, John Wiley & Sons Inc., New York, pages 106-107 and pp. 403-404 for the
 * original protocol.
 *
 * Schneier has each voter get a chit signed for each response option, and no check on
 * whether any given voter votes more than once on a given question. For the use case
 * that Scheiner is invisioning&mdash; there are only two options to choose between,
 * e.g. "yes" and "no"&mdash; it's not an effective attack to vote more than one chit;
 * if I vote for both "yes" and "no" I effectively cancel out my own vote, and thus
 * disenfranchise myself on that question. However, in a 3 or more way race, we need to
 * limit the number of votes per voter (1 per question in the non-ranked-choice case).
 * If "Fritos", "Cheetos", and "Pita Chips" are running, and I really want "Fritos" to
 * lose, it would be in my interest to cast ballots for both "Cheetos" and "Pita Chips"
 * if I can get away with it. But this violates the one vote per voter expectation.
 *
 * One solution to this would be to allow the signing of only one chit per voter per
 * question. I don't see why Schneier doesn't recommend this. Under such a protocol,
 * the CTF "sees" only the chit you ultimately cast&mdash; which is okay, because it comes
 * in for signing, identifed by Voter in blinded form. However, we do in fact want the
 * Voter to be able to cast multiple votes per Question in the case of ranked-choice. (Still
 * limited to one per slot, though&mdahs; i.e. only one for first choice, one for second
 * choice etc.)
 *
 * To allow the possibility of multiple votes per Voter on a question for ranked-choice&mdash;
 * and to link a voter's votes on a particular question, as is required for ranked-choice&mdash;
 * I introduce the "me chit": an additional chit per question. This also has a blind signature
 * applied, so that the CTF knows that the CTF itself signed the chit but not for whom. A valid
 * "me" chit has to be supplied with each vote cast, and the CTF checks that each vote has a
 * distinct "me" chit associated with it (or, in the case of ranked-choice, each of the
 * choices from 1 to n of a set come in with a particular "me" chit.)
 *
 * This opens up another ballot stuffing attack vulnerability, though: as a dishonest Voter, I
 * could have additional "me" chits signed with different numbers in place of scorned options.
 * I.e., where a well-behaved client is expected to have chits like this set signed:
 * Q1. Fritos 4523
 * Q1. Cheetos 536
 * Q1. Pita Chips 7346
 * Q1. me 9034
 * The dishonest Voter gets these 4 chits signed instead:
 * Q1. Pita Chips 379
 * Q1. me 1346
 * Q1. Pita Chips 7346
 * Q1. me 9034
 * Then I could cast two votes for Pita Chips, with different me chits, so that they appear to be
 * from different Voters.
 *
 * One solution to this&mdash; the solution to be employed here&mdash; is to have "me" chits signed using
 * a different key, limit one per Voter per Question. And, in fact, we need to have the "me" chits signed
 * with a different key for each Question. (If we use the same key for me chits for all Questions, then
 * a dishonest Voter who does not care about the outcome of Question 1 could, in place of Question 1's
 * me chit, get an extra me chit signed for the next Question.)
 *
 * I don't see any vulnerability in using the same key to sign all response chits for all Quesitons.
 * Regardless of what I get signed in the catagory of Response Chits, I can still vote for only one
 * option, because I got only one me chit for that question. (Or only one for 1st choice, one for 2nd
 * choice, etc. in the case of ranked-choice.)
 *
 * Thus, when the CentralTabulatingFacility is instantiated, it generates one RSA key pair to be used
 * for signing Response Chits. When a Question is posted, acquires its own RSA key pair, for signing
 * me chits associated with itself. Thus, CentralTabulatingFacility and Question have some common
 * functionality relevant to generating keys, signing, and confirming signatures, inherited from
 * SigningEntity.
 *
 * The need for a Question to keep an RSA key pair has lifecycle implications for the Question object.
 * An RSA key pair is NOT persisted to the database; this would introduce a possible vulnerability to
 * an inside-the-organization bad actor. Thus, a Question object needs to be kept in-memory, not gc'd,
 * during its entire polling time, rather than re-fetching from the database.
 * When a Question is created, it's in the "new" state, and it is persisted.
 * When it's posted, it's added to the postedQuestions collection. This keeps it from being gc'd. It's
 *  detatched from the EntityManager when the EntityManager is closed for that interaction.
 * We always look for a Question object in postedQuestions before consulting the database.
 * When there's a change to the Question, a merge operation updates the database.
 *
 * Separate endpoints are available to the client to request the signing of response chits and signing
 * the me chit&mdash; ballot/{quid}/sign and ballot/{quid}/signme respectively. These are signed by the CTF
 * itself or delegated to the Question respectively for signing, and then, later, for verifying.
 * On the client side, when a Voter receives a list of votalbe questions, each Question comes with the
 * public key and modulus that will be used for signing the me chit. Each Chit chooses its secret k to
 * work with either the CTF's response-chit signing key or the Question's specific me-chit signing key,
 * depending on type of Chit.
 *
 * When a Vote is received, the CTF verifies the signature on the response chit itself, but delegates
 * verifying the signature on the me chit to the Question.
 */
@Component("ctf")
public class CentralTabulatingFacility extends SigningEntity {
    /** See {@link VoterListManager} . */
    @Autowired
    private VoterListManager voterListManager;
    /** Our connection to the magical Hibernate Java Persistence API implementation. */
    @Autowired
    private EntityManagerFactory emf;

    /** Regex pattern for picking the data out of chits, as put together by the client. */
    static Pattern chitPattern = Pattern.compile("^(\\d+) (\\d+) (.*)");
    private Map<String, Map<Long, Set<String>>> blindedChitsPerVoterPerQuestion;
    private Map<Long, Question> postedQuestions;

    public CentralTabulatingFacility() throws NoSuchAlgorithmException {
        initializeKeys();
        blindedChitsPerVoterPerQuestion = new HashMap();
        postedQuestions = new HashMap();
    }

    public Question lookUpQuestion(long quid) {
        Question theQuestion;
        theQuestion = postedQuestions.get(quid);
        if (theQuestion == null) {
            EntityManager em = emf.createEntityManager();
            // Look up the question in database
            theQuestion = em.find(Question.class, quid);
            em.close();
        }
        return theQuestion;
    }

    /*
    Each Voter who is currently of voting status may have a fixed number of response chits signed per question.
    We keep track of the chits received so far for signing. We can re-sign something
    previously signed (as it may have gotten lost on its way back to the client previously); however, if the number of
    chits for a given voter on a given question exceeds the expected number, that's trouble, an apparent attempt at
    ballot box stuffing.
    For now the actual chits received for signing are saved in-memory only, not persisted to the database. Were the process
    to go down, all the currently open Questions would be closed-- no more additional voting on those Question instances
    allowed-- because we are not storing our private key (see above). So there's no point in keeping track across a
    re-start.
    TODO: maybe? save for posterity a record of what Voters had ballots signed for each Question.
     */
    public synchronized String signResponseChit(String blindedMessageText, Voter voter, long quid) {
        if (voter == null || !voter.isAllowedToVote()) {
            TroubleLogger.reportTrouble(String.format("Invalid voter trying to register ballot: %s", voter));
            return null;
        }
        String username = voter.getUsername();
        Question theQuestion = lookUpQuestion(quid);
        if (theQuestion == null) { return null; }
        if (!theQuestion.getStatus().equals("polling")) {
            return null; // could happen if Q closed just as a client loads
        }
        // Check that a voter doesn't register more than one ballot per question.
        Map<Long, Set<String>> blindedChitsPerQuestion
                = blindedChitsPerVoterPerQuestion.computeIfAbsent(username, (x) -> new HashMap<Long, Set<String>>());
        Set<String> blindedChits = blindedChitsPerQuestion.computeIfAbsent(quid, (x) -> new HashSet<String>());
        if (blindedChits.contains(blindedMessageText)) {
            // Same blinded chit submitted again for signing. That's fine. Maybe it didn't get back to the client before.
            // Fall through to below conditional, re-calculate the signed chit and send (again),
        }
        else {
            if (blindedChits.size() >= theQuestion.numberOfAllowedChits()) {
                TroubleLogger.reportTrouble(String.format("Voter %s trying to register excessive chits for %s",
                        username, theQuestion.getText()));
                return null;
            }
            blindedChits.add(blindedMessageText);
        }
        return signText(blindedMessageText);
    }

    public synchronized String signMeChit(String blindedMessageText, Voter voter, long quid) {
        if (voter == null || !voter.isAllowedToVote()) {
            TroubleLogger.reportTrouble(String.format("Invalid voter trying to register ballot: %s", voter));
            return null;
        }
        String username = voter.getUsername();
        Question theQuestion = lookUpQuestion(quid);
        if (theQuestion == null) { return null; }
        if (!theQuestion.getStatus().equals("polling")) {
            return null; // could happen if Q closed just as a client loads
        }
        // Check that a voter doesn't register more than one ballot per question.
        String existingBlindedChit = theQuestion.getBlindedChitForUser(username);
        if (existingBlindedChit == null) {
            // great, we are seeing a me chit on this question from this user for the first time; fall thru
        }
        else if (existingBlindedChit.equals(blindedMessageText)) {
            // great, we are seeing the same blinded me chit (from the same V on the same Q); full through
            // and re-calculate the same thing as before
        }
        else {
            // Boo, this user sent a me chit for this question earlier, and it doesn't match what we just got!
            TroubleLogger.reportTrouble(String.format("Voter %s trying to register extra me chit for %s",
                    username, theQuestion.getText()));
            return null;
        }
        theQuestion.setBlindedChitForUser(username, blindedMessageText);
        return theQuestion.signText(blindedMessageText);
    }

    public synchronized HttpStatus receiveVoteOnQuestion(long quid, VoteMessage vote) {
        if (vote.meChit == null || vote.meChit.length() < 1) {
            TroubleLogger.reportTrouble("Empty me chit submitted");
            return HttpStatus.BAD_REQUEST;
        }
        if (vote.meChitSigned == null || vote.meChitSigned.length() < 1) {
            TroubleLogger.reportTrouble("No signature submitted for me chit");
            return HttpStatus.BAD_REQUEST;
        }
        if (vote.responseChit == null || vote.responseChit.length() < 1) {
            TroubleLogger.reportTrouble("Empty response chit submitted");
            return HttpStatus.BAD_REQUEST;
        }
        if (vote.responseChitSigned == null || vote.responseChitSigned.length() < 1) {
            TroubleLogger.reportTrouble("No signature submitted for response chit");
            return HttpStatus.BAD_REQUEST;
        }
        Question theQuestion = lookUpQuestion(quid);
        if (theQuestion == null) {
            TroubleLogger.reportTrouble("Someone trying to vote on an invalid question ID: " + quid);
            return HttpStatus.NOT_FOUND;
        }
        if (!theQuestion.getStatus().equals("polling")) {
            TroubleLogger.reportTrouble("Someone trying to vote on question that's not open: " + theQuestion.getText());
            return HttpStatus.GONE;
        }
        if (!theQuestion.acceptsResponseRank(vote.ranking)) {
            TroubleLogger.reportTrouble("Someone trying to submit too many responses for one question");
            return HttpStatus.FORBIDDEN;
        }
        if (!theQuestion.confirmSignature(vote.meChit, vote.meChitSigned)) {
            TroubleLogger.reportTrouble("Invalid signature: " + vote.meChitSigned);
            return HttpStatus.FORBIDDEN;
        }
        if (!confirmSignature(vote.responseChit, vote.responseChitSigned)) {
            TroubleLogger.reportTrouble("Invalid signature: " + vote.responseChitSigned);
            return HttpStatus.FORBIDDEN;
        }
        try {
            castVote(theQuestion, vote.meChit, vote.responseChit, vote.ranking);
            return HttpStatus.OK;
        }
        catch (Exception ex) {
            TroubleLogger.reportTrouble(ex.getMessage());
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    private synchronized void castVote(Question q, String voterIDChit, String responseChit, int ranking) throws Exception {
        long quid = q.getId();
        String response = null;
        String voterChitNumber = null;
        String responseChitNumber = null;
        Matcher m = chitPattern.matcher(voterIDChit);
        if (m.find()){
            if (quid != Long.parseLong(m.group(1))) {
                throw new Exception("Chit submitted for wrong question: " + voterIDChit );
            }
            voterChitNumber = m.group(2);
        }
        else {
            throw new Exception("Malformed me chit " + voterIDChit);
        }
        m = chitPattern.matcher(responseChit);
        if (m.find()) {
            if (quid != Long.parseLong(m.group(1))) {
                throw new Exception("These chits do not go together: " + voterIDChit + responseChit);
            }
            responseChitNumber = m.group(2);
            response = m.group(3);
        }
        else {
            throw new Exception("Malformed response chit " + responseChit);
        }
        EntityManager em = emf.createEntityManager();
        String hql = "select v from Vote v where v.question = :q and v.voterChitNumber = :voterChitNumber and v.ranking = :ranking";
        Query query= em.createQuery(hql);
        query.setParameter("q", q);
        query.setParameter("voterChitNumber", voterChitNumber);
        query.setParameter("ranking", ranking);
        List<Vote> list = query.getResultList();
        Vote v = null;
        if (list.size() > 0) {
            v = list.get(0);
            em.close();
            if (!v.getResponseChitNumber().equals(responseChitNumber) || !v.getResponse().equals(response)) {
                // Voting twice on the same question (with different choices) is an error.
                throw new Exception(String.format("Contradictory votes from %s on question %d, rank %d. %s, %s", voterIDChit, quid, ranking, v.getResponse(), responseChit));
            }
            // otherwise, same message received twice-- that's fine!
        }
        else {
            v = new Vote(q, response, voterChitNumber, responseChitNumber, ranking);
            em.getTransaction().begin();
            try {
                em.persist(v);
                em.getTransaction().commit();
            }
            catch (Exception ex) {
                em.getTransaction().rollback();
            }
            finally {
                em.close();
            }
        }
    }

    public List<Vote> detailedTabulationForQuestion(long quid) {
        Question q = lookUpQuestion(quid);
        EntityManager em = emf.createEntityManager();
        String hql = "select v from Vote v where v.question = :q";
        Query query= em.createQuery(hql);
        query.setParameter("q", q);
        List<Vote> list = query.getResultList();
        em.close();
        return list;
    }

    public void postQuestion(Question q) throws NoSuchAlgorithmException {
        q.post();
        postedQuestions.put(q.getId(), q);
    }

    public void closeQuestion(Question q) {
        q.close();
        postedQuestions.remove(q.getId());
    }

    public List<Question> votableQuestionList() {
        return new ArrayList(postedQuestions.values());
    }

    public String fillInVotingInfo(String pageText) throws JsonProcessingException {
        pageText = pageText.replaceAll("##EXPONENT##", this.getPublicExponent().toString(10));
        pageText = pageText.replaceAll("##MODULUS##", this.getModulus().toString(10));

        String questionString = new ObjectMapper().writeValueAsString(votableQuestionList());
        pageText = pageText.replaceAll("##QUESTIONS##", questionString);

        return pageText;
    }
}
