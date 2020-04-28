package com.jagbag.dvoting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.persistence.*;

/**
 * Endpoint controller for endpoints related to management of the list of users/voters.
 * An admin user has some ability to manipulate the list of users: they can grant or revote admin or voting
 * privileges. And delete users? With some safeguards?
 * A user can administer their own account: they are free to change their name, and they can change their
 * email address (although this requires confirming the new address). They CANNOT change their username, nor
 * pick a username used by someone else. They can change their password (although that functionality will
 * probably go through LoginController). A user can't change their privileges, though, unless they happen to
 * be an admin.
 */
@RestController
public class UserAdminController extends APIController {
    @Autowired private VoterListManager voterListManager;

    @GetMapping("/voters/{username}")
    public Voter getInfoOnVoter(@RequestHeader HttpHeaders headers, @PathVariable String username) {
        loginManager.validateCanAdministerUser(headers, username);
        Voter v = voterListManager.getForUsername(username);
        if (v == null) {
            throw new ItemNotFoundException();
        }
        return v;
    }

    /*
    admin can change admin, allowedToVote (but not while a question is open)
    TODO: forbid removing admin privs from last single user with admin privs
     */
    @PatchMapping("/voters/{username}/priv")
    public synchronized ResponseEntity patchVoterPrivs(@RequestHeader HttpHeaders headers, @RequestBody Voter patchVoter, @PathVariable String username) {
        loginManager.validateAdminUser(headers);
        Voter v = voterListManager.getForUsername(username);
        if (v == null) {
            throw new ItemNotFoundException();
        }
        v.setAllowedToVote(patchVoter.isAllowedToVote());
        v.setAdmin(patchVoter.isAdmin());
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        try {
            em.merge(v);
            em.getTransaction().commit();
        }
        catch (Exception ex) {
            em.getTransaction().rollback();
            throw new RuntimeException("Cannot save changes to database");
        }
        finally {
            em.close();
        }
        return new ResponseEntity(v, HttpStatus.OK);
    }

    /*
    TODO: the user themself can edit email or name
     */
    @PatchMapping("/voters/{username}")
    public synchronized ResponseEntity patchVoterInfo(@RequestHeader HttpHeaders headers, @RequestBody Voter patchVoter, @PathVariable String username) {
        loginManager.validateCanAdministerUser(headers, username);
        Voter v = voterListManager.getForUsername(username);
        if (v == null) {
            throw new ItemNotFoundException();
        }
        v.setName(patchVoter.getName());
        if (!v.getEmail().equals(patchVoter.getEmail())) {
            // TODO: email updating protocol
        }
        // TODO: merge changes
        return new ResponseEntity(v, HttpStatus.OK);
    }

    /* TODO: if it's an administrator, not the user themself, don't allow deleting a voting-priv user */
    @DeleteMapping("/voters/{username}")
    public synchronized ResponseEntity deleteVoter(@RequestHeader HttpHeaders headers, @PathVariable String username) {
        loginManager.validateCanAdministerUser(headers, username);
        Voter v = voterListManager.getForUsername(username);
        if (v == null) {
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }
        voterListManager.removeVoter(v);
        return new ResponseEntity(HttpStatus.OK);
    }

}
