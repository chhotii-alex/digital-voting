package com.jagbag.dvoting;

import net.minidev.json.JSONObject;  // TODO: are we linking an extraneous dependency to make this work?
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import javax.persistence.EntityManager;
import java.util.List;

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

    /* return public key and modulus */
    @GetMapping("/ballots/keys")
    public String getKeys(@RequestHeader HttpHeaders headers) {
        loginManager.validateVotingUser(headers);
        JSONObject obj = new JSONObject();
        obj.put("public", ctf.getPublicExponent().toString(10));
        obj.put("modulus", ctf.getModulus().toString(10));
        return obj.toString();
    }

    /* return currently open questions */
    @GetMapping("/ballots")
    public ResponseEntity<List<Question>> getOpenQuestions(@RequestHeader HttpHeaders headers) {
        loginManager.validateVotingUser(headers);
        List<Question> list = ctf.votableQuestionList();
        return ResponseEntity.ok(list);
    }

    @PostMapping(value="ballot/{quid}/sign")
    public ResponseEntity getChitSigned(@RequestHeader HttpHeaders headers,
                                        @PathVariable long quid, @RequestBody PostPayload chit) {
        Voter v = loginManager.validateVotingUser(headers);
        String result = ctf.signResponseChit(chit.getB(), v, quid);
        if (result == null) {
            throw new ForbiddenException();
        }
        return new ResponseEntity(result, HttpStatus.OK);
    }

    @PostMapping(value="ballot/{quid}/signme")
    public ResponseEntity getMeChitSigned(@RequestHeader HttpHeaders headers,
                                        @PathVariable long quid, @RequestBody PostPayload chit) {
        Voter v = loginManager.validateVotingUser(headers);
        String result = ctf.signMeChit(chit.getB(), v, quid);
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
        return new ResponseEntity(result);
    }

    @GetMapping(value="ballot/{quid}/verify")
    public ResponseEntity getVoteVerificationInfo(@RequestHeader HttpHeaders headers,
                                                  @PathVariable int quid) {
        loginManager.validatePrivilegedUser(headers);
        List<Vote> votes = ctf.detailedTabulationForQuestion(quid);
        return new ResponseEntity(votes, HttpStatus.OK);
    }
}
