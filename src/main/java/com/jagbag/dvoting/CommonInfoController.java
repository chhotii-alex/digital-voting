package com.jagbag.dvoting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Endpoint handler to
 * respond to requests for info that both admins and voters should be able to see.
 */
@RestController
public class CommonInfoController extends APIController {
    @Autowired
    private VoterListManager voterListManager;
    @Autowired
    private CentralTabulatingFacility ctf;

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
     * Get a Question through the JSON API, given a question ID
     *
     * @param quid - id of a Question
     * @return A Question object, with its ResponseOptions
     */
    @GetMapping("/questions/{quid}")
    public Question getInfoOnQuestion(@RequestHeader HttpHeaders headers, @PathVariable int quid) {
        loginManager.validatePrivilegedUser(headers);
        Question q = ctf.lookUpQuestion(quid);
        if (q == null) {
            throw new ItemNotFoundException();
        }
        return q;
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
            /* TODO: option to see accounts that are not activated */
            if (voter.isActiveAccount()) {
                if ((voter.isAllowedToVote() && includeCanVote) || (!voter.isAllowedToVote() && includeCannotVote)) {
                    results.add(voter);
                }
            }
        }
        return results;
    }

    @GetMapping("/pp")
    public List<Voter> possibleProxies(@RequestHeader HttpHeaders headers) {
        Voter inquirer = loginManager.validatePrivilegedUser(headers);
        List<Voter> results = new ArrayList<Voter>();
        for (Voter voter : voterListManager.voters()) {
            if (voter.isActiveAccount()) {  // only list active accounts...
                if (!inquirer.equals(voter)) { // of OTHER people...
                    if (voter.isAllowedToVote()) {  // who have voting privilege...
                        if (voter.getProxyHolder() == null) { // who are personally going to vote, not thru a proxy...
                            if (voter.getAcceptedProxyGrantees().size() < 2) { // and aren't already holding 2 proxies
                                results.add(voter);
                            }
                        }
                    }
                }
            }
        }
        return results;
    }

    /**
     * Text template for the body of the email requesting proxy holding.
     */
    @Value("classpath:static/proxy_request.txt")
    protected Resource requestProxyTemplate;

    /**
     * Text template for the body of the email requesting proxy holding.
     */
    @Value("classpath:static/proxy_termination.txt")
    protected Resource cancelProxyTemplate;

    @GetMapping(value = "voters/requestproxy")
    public HttpStatus requestProxy(@RequestHeader HttpHeaders headers,
                                   @RequestParam(required = false) String proxy) throws IOException {
        Voter grantor = loginManager.validateVotingUser(headers);
        if (proxy == null) {
            Voter currentProxyHolder = grantor.getProxyHolder();
            if (currentProxyHolder != null) {
                grantor.setProxyHolder(null);
                voterListManager.updateVoter(grantor);
                // send email informing person who was proxy holder
                String text = APIController.textFromResource(cancelProxyTemplate);
                text = text.replaceAll("##PROXYNAME##", currentProxyHolder.getName());
                text = text.replaceAll("##NAME##", grantor.getName());
                emailSender.sendEmail(currentProxyHolder.getEmail(), "Digital Voting proxy status", text);
            }
            return HttpStatus.OK;
        }
        Voter proxyVoter = voterListManager.getForUsername(proxy);
        if (proxyVoter == null) {
            return HttpStatus.NOT_FOUND;
        }
        if (!proxyVoter.isAllowedToVote()) {
            return HttpStatus.GONE;
        }
        if (!proxyVoter.canAcceptProxy()) {
            return HttpStatus.GONE;
        }
        if (proxyVoter.requestProxyHolding(grantor)) {
            String acceptURL = String.format("%s/voters/proxyaccept?proxy=%s&grantor=%s", hostBaseURL,
                    URLEncoder.encode(proxyVoter.getUsername(), StandardCharsets.UTF_8.toString()),
                    URLEncoder.encode(grantor.getUsername(), StandardCharsets.UTF_8.toString()));
            String refuseURL = String.format("%s/voters/proxyrefuse?proxy=%s&grantor=%s", hostBaseURL,
                    URLEncoder.encode(proxyVoter.getUsername(), StandardCharsets.UTF_8.toString()),
                    URLEncoder.encode(grantor.getUsername(), StandardCharsets.UTF_8.toString()));

            String text = APIController.textFromResource(requestProxyTemplate);
            text = text.replaceAll("##PROXYNAME##", proxyVoter.getName());
            text = text.replaceAll("##NAME##", grantor.getName());
            text = text.replaceAll("##ACCEPT_URL##", acceptURL);
            text = text.replaceAll("##REFUSE_URL##", refuseURL);
            emailSender.sendEmail(proxyVoter.getEmail(), "Digital Voting proxy request", text);
            Set<Voter> affectedVoters = new HashSet<Voter>();
            affectedVoters.add(proxyVoter);
            affectedVoters.add(grantor);
            voterListManager.updateVoters(affectedVoters);
            return HttpStatus.OK;
        } else {
            return HttpStatus.FORBIDDEN;
        }
    }

    /**
     * Text template for the body of the email telling s.o. that their request for another to hold
     * their proxy has been turned down.
     */
    @Value("classpath:static/proxy_refused.txt")
    protected Resource proxyRefusedTemplate;

    @GetMapping(value = "voters/proxyrefuse")
    public ResponseEntity refuseRequestProxy(@RequestParam String proxy, @RequestParam String grantor) throws IOException {
        Voter grantingVoter = voterListManager.getForUsername(grantor);
        if (grantingVoter == null) {
            // Shouldn't happen... unless... someone clicks on a link in a really old email;
            // or mangles pasting the link
            return ResponseEntity.ok().body(String.format("I don't know who %s is, but, ok", grantor));
        }
        Voter proxyHolder = voterListManager.getForUsername(proxy);
        if (grantingVoter.getProxyHolder().equals(proxyHolder)) {
            grantingVoter.setProxyHolder(null);
            voterListManager.updateVoter(grantingVoter);
            // send email informing the person of this
            String text = APIController.textFromResource(proxyRefusedTemplate);
            text = text.replaceAll("##PROXYNAME##", proxyHolder.getName());
            text = text.replaceAll("##NAME##", grantingVoter.getName());
            emailSender.sendEmail(proxyHolder.getEmail(), "Digital Voting proxy status", text);
        }
        return ResponseEntity.ok().body("Thank you");
    }

    @GetMapping(value = "voters/proxyaccept")
    public ResponseEntity acceptRequestProxy(@RequestParam String proxy, @RequestParam String grantor) {
        Voter grantingVoter = voterListManager.getForUsername(grantor);
        if (grantingVoter == null) {
            return ResponseEntity.ok().body(String.format("No proxy: I don't know who %s is.", grantor));
        }
        Voter proxyHolder = voterListManager.getForUsername(proxy);
        if (!grantingVoter.getProxyHolder().equals(proxyHolder)) {
            return ResponseEntity.ok().body("Too late; they have given their proxy to someone else.");
        }
        if (proxyHolder.canAcceptProxy()) {
            grantingVoter.setProxyAccepted(true);
            voterListManager.updateVoter(grantingVoter);
            return ResponseEntity.ok().body("Thank you");
        }
        else {
            return ResponseEntity.ok().body("You cannot accept this person's proxy. Are you holding two already? "
                    + "Consider refusing this proxy request instead, so they know to seek another proxy holder.");
        }
    }
}

