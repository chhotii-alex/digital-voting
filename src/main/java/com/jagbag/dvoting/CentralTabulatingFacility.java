package com.jagbag.dvoting;

import org.springframework.beans.factory.annotation.Autowired;
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
 */
@Component("ctf")
public class CentralTabulatingFacility {
    /** See {@link VoterListManager} . */
    @Autowired
    private VoterListManager voterListManager;
    /** Our connection to the magical Hibernate Java Persistence API implementation. */
    @Autowired
    private EntityManagerFactory emf;

    /** Regex pattern for picking the data out of chits, as put together by the client. */
    static Pattern chitPattern = Pattern.compile("^(\\d+) (\\d+) (.*)");
    private Map<String, Map<Long, Set<String>>> blindedChitsPerVoterPerQuestion;
    private KeyPair rsaKeys;

    /*
    Note, there is no storage of the key. The key is only kept in memory.
    This means that if the service is re-started, there is no persistence of the key from one
    run to the next.
    This means that the CTF cannot confirm signatures on ballots that were signed in a previous run.
    If a poll is started, and voters have their ballots signed, and then the service is re-started, all the
    existing signed ballots are invalidated. We can't have the voters who haven't voted get their ballots re-signed,
    because we do not know who has and hasn't voted-- thus, if we allow re-signing ballots after a restart, voters who
    voted before the re-start would have the opportunity to vote twice. Without any storage of the key, upon start-up,
    we need to close polling on all questions, and the only way to proceed would be to start a fresh round of polling
    by posing the same question again as a new question.
    TODO: value the pollCloseWhen field on any open questions found in the database upon starting.

    What might we do to avoid this? (How likely is a re-start of the service, anyway?) Storage of the key to disk
    means that it could be stolen. I assume that anyone who has administrator access is not a disinterested party
    (that's why we bother with all this blind signing, after all) and thus storing the key would be a huge hole in the
    security of this voting system.

    Alternatively, we could have at least one voter from each party or faction type in a passphrase, which is never
    stored; each passphrase is added to the private key; and only the sum of the private key and all the passphrases
    is written to disk. (Along with the public key and modulus as cleartext, of course, as those are public knowledge.)
    We would want to store a record of exactly which voters supplied a passphase.
    Upon re-start, a previous session's private key could be recovered by having each of the voters whose passphrase
    was used to mask the passphrase type in the same phrase again, to undo the transformation.
    Of course, this would only work if each person is able to enter the exact same text the second time, otherwise
    all is lost. Probably too unreliable to not be utterly frustrating. Unless, as a convenience, the client app
    offers to save these in local storage?
     */
    private static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, new SecureRandom());
        KeyPair pair = generator.generateKeyPair();

        return pair;
    }

    public CentralTabulatingFacility() throws NoSuchAlgorithmException {
        blindedChitsPerVoterPerQuestion = new HashMap();
        rsaKeys = generateKeyPair();
        System.out.println("public:  " + getPublicExponent().toString(10));
        System.out.println("modulus: " + getModulus().toString(10));
    }

    private RSAPublicKey getPublicKey() {
        return (RSAPublicKey)(rsaKeys.getPublic());
    }

    private BigInteger getPrivateExponent() {
        return ((RSAPrivateKey)rsaKeys.getPrivate()).getPrivateExponent();
    }

    /**
     * Get the modulus for the RSA key pair used for signing chits.
     */
    public BigInteger getModulus() {
        return getPublicKey().getModulus();
    }

    /**
     * Get the public key of the RSA key pair used for signing chits.
     * The public key and modulus are sent to the client, and can be shouted from the rooftops.
     * Note the lack of any public method exposing the private key, however.
     */
    public BigInteger getPublicExponent() {
        return getPublicKey().getPublicExponent();
    }

    /*
    Convert a BigInteger to a relatively compact string form.
     */
    public static String encoded(BigInteger value) {
        return value.toString(36);
    }

    /*
    Read a BigInteger from the relatively compact string form.
     */
    public static BigInteger decoded(String s) {
        return new BigInteger(s, 36);
    }

    public Question lookUpQuestion(long quid) {
        EntityManager em = emf.createEntityManager();
        // Look up the question
        Question theQuestion = em.find(Question.class, quid);
        em.close();
        return theQuestion;
    }

    /*
    Each Voter who is currently of voting status may have a fixed number of chits signed per question: one for each
    response option, plus one. We keep track of the chits received so far for signing. We can re-sign something
    previously signed (as it may have gotten lost on its way back to the client previously); however, if the number of
    chits for a given voter on a given question exceeds the expected number, that's trouble, an apparent attempt at
    ballot box stuffing.
    For now the actual chits received for signing are saved in-memory only, not persisted to the database. Were the process
    to go down, all the currently open Questions would be closed-- no more additional voting on those Question instances
    allowed-- because we are not storing our private key (see above). So there's no point in keeping track across a
    re-start.
    However, we do save for posterity a record of what Voters had ballots signed for what Questions.
     */
    public synchronized String signChit(String blindedMessageText, Voter voter, long quid) {
        if (voter == null || !voter.isAllowedToVote()) {
            TroubleLogger.reportTrouble(String.format("Invalid voter trying to register ballot: %s", voter));
            return null;
        }
        String username = voter.getUsername();
        Question theQuestion = lookUpQuestion(quid);
        if (!theQuestion.getStatus().equals("polling")) {
            return null; // TODO: user experience if signing chits fails; possible if Q closed just as they load
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
                TroubleLogger.reportTrouble(String.format("Voter with email %s trying to register excessive chits for %s",
                        username, theQuestion.getText()));
                return null;
            }
            blindedChits.add(blindedMessageText);
        }
        BigInteger t = decoded(blindedMessageText);
        RSAPrivateKey key = (RSAPrivateKey)(rsaKeys.getPrivate());
        BigInteger d = key.getPrivateExponent();
        BigInteger n = key.getModulus();
        BigInteger signedT = t.modPow(d, n);  // sign by encrypting with private key
        String signedAsString = encoded(signedT);
        return signedAsString;
    }

    public synchronized void receiveVoteOnQuestion(long quid, VoteMessage vote) {
        if (vote.meChit == null || vote.meChit.length() < 1) {
            TroubleLogger.reportTrouble("Empty me chit submitted");
            return;
        }
        if (vote.meChitSigned == null || vote.meChitSigned.length() < 1) {
            TroubleLogger.reportTrouble("No signature submitted for me chit");
            return;
        }
        if (vote.responseChit == null || vote.responseChit.length() < 1) {
            TroubleLogger.reportTrouble("Empty response chit submitted");
            return;
        }
        if (vote.responseChitSigned == null || vote.responseChitSigned.length() < 1) {
            TroubleLogger.reportTrouble("No signature submitted for response chit");
            return;
        }
        if (!confirmSignature(vote.meChit, vote.meChitSigned)) {
            TroubleLogger.reportTrouble("Invalid signature: " + vote.meChitSigned);
            return;
        }
        if (!confirmSignature(vote.responseChit, vote.responseChitSigned)) {
            TroubleLogger.reportTrouble("Invalid signature: " + vote.responseChitSigned);
            return;
        }
        Question theQuestion = lookUpQuestion(quid);
        if (theQuestion == null) {
            TroubleLogger.reportTrouble("Someone trying to vote on an invalid question ID: " + quid);
            return;
        }
        if (!theQuestion.getStatus().equals("polling")) {
            TroubleLogger.reportTrouble("Someone trying to vote on question that's not open: " + theQuestion.getText());
            return;
        }
        try {
            castVote(theQuestion, vote.meChit, vote.responseChit);
        }
        catch (Exception ex) {
            TroubleLogger.reportTrouble(ex.getMessage());
        }
    }

    private synchronized void castVote(Question q, String voterIDChit, String responseChit) throws Exception {
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
        String hql = "select v from Vote v where v.question = :q and v.voterChitNumber = :voterChitNumber";
        Query query= em.createQuery(hql);
        query.setParameter("q", q);
        query.setParameter("voterChitNumber", voterChitNumber);
        List<Vote> list = query.getResultList();
        Vote v = null;
        if (list.size() > 0) {
            em.close();
            v = list.get(0);
            if (!v.getResponseChitNumber().equals(responseChitNumber) || !v.getResponse().equals(response)) {
                // Voting twice on the same question (with different choices) is an error.
                throw new Exception(String.format("Contradictory votes from %s on question %d. %s, %s", voterIDChit, quid, v.getResponse(), responseChit));
            }
            // otherwise, same message received twice-- that's fine!
        }
        else {
            v = new Vote(q, response, voterChitNumber, responseChitNumber);
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

    private boolean confirmSignature(String chit, String signedChit) {
        BigInteger m = new BigInteger(chit.getBytes());
        BigInteger s = decoded(signedChit);
        BigInteger alleged = s.modPow(getPublicExponent(), getModulus());
        return (alleged.equals(m));
    }

    public List<Vote> detailedTabulationForQuestion(long quid) {
        Question q = lookUpQuestion(quid);
        EntityManager em = emf.createEntityManager();
        String hql = "select v from Vote v where v.question = :q";
        Query query= em.createQuery(hql);
        query.setParameter("q", q);
        List<Vote> list = query.getResultList();
        return list;
    }
}
