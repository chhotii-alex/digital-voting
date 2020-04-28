package com.jagbag.dvoting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Endpoint handler to
 * respond to requests for info that both admins and voters should be able to see.
 */
@RestController
public class CommonInfoController extends APIController {
    @Autowired private VoterListManager voterListManager;

    @GetMapping("/questions")
    public ResponseEntity<List<Question>> getQuestions(@RequestHeader HttpHeaders headers) {
        loginManager.validatePrivilegedUser(headers);
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        String hql = "select q from Question q order by q.createdWhen";
        List<Question> list = em.createQuery(hql).getResultList();
        em.getTransaction().commit();
        em.close();
        return ResponseEntity.ok(list);
    }

    /**
     * getInfoOnQuestion
     * @param quid - id of a Question
     * @return A Question object, with its ResponseOptions, plus all relevant Signed Ballots, and Votes
     * TODO: implement getInfoOnQuestion
     */
    @GetMapping("/questions/{quid}")
    public Voter getInfoOnQuestion(@RequestHeader HttpHeaders headers, @PathVariable int quid) {
        loginManager.validatePrivilegedUser(headers);
        //     Quesion q = questionManager.getForQuestionId(quid);
   //     if (q == null) {
            throw new ItemNotFoundException();
    //    }
    //    return q;
    }

    @GetMapping("/voters")
    public List<Voter> getVoters(@RequestHeader HttpHeaders headers,
                                 @RequestParam(value = "canvote", defaultValue = "all", required = false)
                                         String filterValue) {
        loginManager.validatePrivilegedUser(headers);
        List<Voter> results = new ArrayList<Voter>();
        boolean includeCanVote = false;
        boolean includeCannotVote = false;
        switch (filterValue) {
            case "all":
                includeCanVote = true;
                includeCannotVote = true;
                break;
            case "yes":
                includeCanVote = true;
                break;
            case "no":
                includeCannotVote = true;
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Parameter should be yes, no, or all");
        }
        for (Voter voter : voterListManager.voters()) {
            if (voter.isActiveAccount()) {
                if ((voter.isAllowedToVote() && includeCanVote) || (!voter.isAllowedToVote() && includeCannotVote)) {
                    results.add(voter);
                }
            }
        }
        return results;
    }


}
