package com.jagbag.dvoting.controllers;

import com.jagbag.dvoting.*;
import com.jagbag.dvoting.entities.*;
import com.jagbag.dvoting.exceptions.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for endpoints for the actual process of voting.
 * First, a voter (or rather the JS running in their browser) needs to know the CTF's public key and
 * modulus to make the blind signing math work.
 * And they need to know the list of open questions that they can currently vote on.
 * Next they submit blinded versions of their chits for each ballot to be signed.
 * Next, after choosing the option that they want to vote for, they submit the un-blinded signed chits
 * representing their chosen options.
 * Finally, they can (and should) obtain the complete list of tabulated votes for each question, so that they
 * can verify that 1) the secret numeric identifiers for their votes appear in the list of votes counted, and
 * 2) the vote totals from their own count of the votes agree with those of the election authority.
 */
@RestController
public class VotingAPIController extends APIController {
    @Autowired private CentralTabulatingFacility ctf;
    @Autowired private LoginController loginController;
    @Autowired private VoterListManager voterListManager;

    /** File containing the voting page */
    @Value("classpath:static/voteapp.html")
    protected Resource votingPageTemplate;

    @GetMapping("/voting")
    public ResponseEntity getVotingPage(@RequestHeader HttpHeaders headers,
                                        @RequestParam(required = false) String behalf) throws Exception {
        Voter v = null;
        Voter proxyGrantee = null;
        Voter effectiveVoter = null;
        try {
            v = loginManager.validateBasicUser(headers);
        }
        catch (Exception ex) {
            return redirectToPage("loggedout.html");
        }
        if (!v.isAllowedToVote()) {
            return loginController.getLanding(headers);
        }
        effectiveVoter = v;
        if (behalf != null) {
            proxyGrantee = voterListManager.getForUsername(behalf);
            if (proxyGrantee == null) {
                return ResponseEntity.notFound().build();
            }
            if (!v.equals(proxyGrantee.getProxyHolder())) {   // if you're not their proxy holder...
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            if (!proxyGrantee.isProxyAccepted()) {   // if you haven't accepted their proxy officially...
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            effectiveVoter = proxyGrantee;
        }
        String pageText = textFromResource(votingPageTemplate);
        pageText = fillInVotingInfo(pageText, effectiveVoter);
        return ResponseEntity.ok().body(pageText);
    }

    public String fillInVotingInfo(String pageText, Voter effectiveVoter) throws JsonProcessingException {
        pageText = pageText.replaceAll("##EXPONENT##", ctf.getPublicExponent().toString(10));
        pageText = pageText.replaceAll("##MODULUS##", ctf.getModulus().toString(10));
        pageText = pageText.replaceAll("##VOTER##", effectiveVoter.getUsername());
        pageText = pageText.replaceAll("##VOTERNAME##", effectiveVoter.getName());

        String questionString = new ObjectMapper().writeValueAsString(ctf.votableQuestionList());
        pageText = pageText.replaceAll("##QUESTIONS##", questionString);

        return pageText;
    }

    /* return public key and modulus */
    @GetMapping("/ballots/keys")
    public String getKeys(@RequestHeader HttpHeaders headers) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> obj = new HashMap<String, String>();
        obj.put("public", ctf.getPublicExponent().toString(10));
        obj.put("modulus", ctf.getModulus().toString(10));
        return objectMapper.writeValueAsString(obj);
    }

    /* return currently open questions */
    @GetMapping("/ballots")
    public ResponseEntity<List<Question>> getOpenQuestions(@RequestHeader HttpHeaders headers) {
        loginManager.validateVotingUser(headers);
        List<Question> list = ctf.votableQuestionList();
        return ResponseEntity.ok(list);
    }

    /**
     * Find and validate the Voter for whom this voting action is being done.
     * Normally, you would think, this would be the logged-in user. However, if the "behalf"
     * query parameter is supplied, the logged-in user is asking on behalf of one whose proxy
     * they hold. Validate that the user who (supposedly) has granted the proxy is, in fact,
     * a user with voting privileges; has granted their proxy to the logged-in user; and that
     * the proxy has been accepted.
     * @param headers tell us who is logged in
     * @param behalf may be null; if not, should be a valid username; either the logged-in user or
     *               one who has granted their proxy to the logged-in user
     * @return Voter object representing the person on whose behalf we will vote
     */
    protected Voter getEffectiveVoter(@RequestHeader HttpHeaders headers, String behalf) {
        Voter v = loginManager.validateVotingUser(headers);
        if (behalf != null) {
            Voter effectiveVoter = voterListManager.getForUsername(behalf);
            if (effectiveVoter == null) {
                throw new ItemNotFoundException();
            }
            if (!effectiveVoter.isAllowedToVote()) {
                throw new ForbiddenException();
            }
            if (!effectiveVoter.equals(v)) {
                if (!v.equals(effectiveVoter.getProxyHolder())) {
                    throw new ForbiddenException();
                }
                if (!effectiveVoter.isProxyAccepted()) {
                    throw new ForbiddenException();
                }
                v = effectiveVoter;
            }
        }
        return v;
    }

    @PostMapping(value="ballot/{quid}/sign")
    public ResponseEntity getChitSigned(@RequestHeader HttpHeaders headers,
                                        @PathVariable long quid, @RequestParam(required = false) String behalf,
                                        @RequestBody PostPayload chit) {
        Voter v = getEffectiveVoter(headers, behalf);
        Question q = ctf.lookupPostedQuestion(quid);
        if (q == null) {
            throw new ItemNotFoundException();
        }
        String result = ctf.signResponseChit(chit.getB(), v, q);
        if (result == null) {
            throw new ForbiddenException();
        }
        return new ResponseEntity(result, HttpStatus.OK);
    }

    @PostMapping(value="ballot/{quid}/signme")
    public ResponseEntity getMeChitSigned(@RequestHeader HttpHeaders headers,
                                        @PathVariable long quid, @RequestParam(required = false) String behalf,
                                          @RequestBody PostPayload chit) {
        Voter v = getEffectiveVoter(headers, behalf);
        Question q = ctf.lookupPostedQuestion(quid);
        if (q == null) {
            throw new ItemNotFoundException();
        }
        String result = ctf.signMeChit(chit.getB(), v, q);
        if (result == null) {
            throw new ForbiddenException();
        }
        return new ResponseEntity(result, HttpStatus.OK);
    }

    @PostMapping(value="ballot/{quid}/vote")
    public ResponseEntity submitVote(@PathVariable int quid, @RequestBody VoteMessage vote) {
        /* Pointedly NOT verifying that this is a logged-in voter.
        We will allow voting through a proxy, from a not-logged-in page. The fact that the ballot is
        signed authenticates it.
         */
        HttpStatus result = ctf.receiveVoteOnQuestion(quid, vote);
        return new ResponseEntity(vote.ranking, result);
    }

    @PostMapping(value="ballot/{quid}/vote_rank")
    public ResponseEntity submitVoteList(@PathVariable int quid, @RequestBody List<VoteMessage> votes) {
        /* Pointedly NOT verifying that this is a logged-in voter.
        We will allow voting through a proxy, from a not-logged-in page. The fact that the ballot is
        signed authenticates it.
         */
        HttpStatus result = HttpStatus.OK;
        for (VoteMessage vote : votes) {
            result = ctf.receiveVoteOnQuestion(quid, vote);
            if (result != HttpStatus.OK) {
                break;
            }
        }
        return new ResponseEntity(0, result);
    }

    @GetMapping(value="ballot/{quid}/vote_get")
    public ResponseEntity submitVoteViaQuery(@PathVariable int quid, @RequestParam String data) {
        VoteMessage vote;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            vote = objectMapper.readValue(data, VoteMessage.class);
        }
        catch (JsonProcessingException e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Could not read data from URL.");
        }
        HttpStatus result = ctf.receiveVoteOnQuestion(quid, vote);
        return ResponseEntity.ok().body("Thank you! Your vote will be tallied.");
    }

    @GetMapping(value="ballot/{quid}/vote_get_rank")
    public ResponseEntity submitRankVoteViaQuery(@PathVariable int quid, @RequestParam String data) {
        List<VoteMessage> votes = null;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            votes = objectMapper.readValue(data, new TypeReference<List<VoteMessage>>(){});
        }
        catch (JsonProcessingException e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Could not read data from URL.");
        }
        HttpStatus result = HttpStatus.OK;
        for (VoteMessage vote : votes) {
            result = ctf.receiveVoteOnQuestion(quid, vote);
            if (result != HttpStatus.OK) {
                break;
            }
        }
        return new ResponseEntity("Thank you! Your vote will be tallied.", result);
    }

    @GetMapping(value="ballot/{quid}/verify")
    public ResponseEntity getVoteVerificationInfo(@RequestHeader HttpHeaders headers,
                                                  @PathVariable int quid) {
        /*
        Just as you don't need to be logged in to vote (using an already-signed chit) you shouldn't
        need to be logged in to verify that it went through.
         */
        List<Vote> votes = ctf.detailedTabulationForQuestion(quid);
        return new ResponseEntity(votes, HttpStatus.OK);
    }
}
