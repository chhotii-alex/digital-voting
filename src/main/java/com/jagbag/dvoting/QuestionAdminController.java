package com.jagbag.dvoting;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import javax.persistence.*;

/**
 * Handles endpoints related to managing the list of questions: creating, editing, posting, and closing questions.
 * Any user with admin status may manage the question list.
 */
@RestController
public class QuestionAdminController extends APIController {
    /* This ONLY should be called for new questions-- questions not previously fetched from the database. */
    @PostMapping(value="questions")
    public ResponseEntity addQuestion(@RequestHeader HttpHeaders headers, @RequestBody Question q) {
        loginManager.validateAdminUser(headers);
        boolean success = false;
        q.setCreateDateTime();
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(q);
            for (ResponseOption opt : q.getPossibleResponses()) {
                em.persist(opt);
            }
            em.getTransaction().commit();
            success = true;
        }
        catch (Exception ex) {
            // TODO: log this
            ex.printStackTrace();
            em.getTransaction().rollback();
        }
        finally {
            em.close();
        }
        if (success) {
            return new ResponseEntity<Question>(q, HttpStatus.CREATED);
        }
        else {
            return new ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /*
    Update question text. Add, remove, or alter ResponseOptions.
    TODO: make sure, in the client, that response options all have unique text.
    TODO: in the client, buttons to delete a response option, or add one.
     */
    @PatchMapping("/questions/{quid}")
    public synchronized ResponseEntity patchQuestion(@RequestHeader HttpHeaders headers,
                                        @RequestBody Question patchQuestion, @PathVariable long quid) {
        loginManager.validateAdminUser(headers);
        EntityManager em = emf.createEntityManager();
        try {
            Question q = em.find(Question.class, quid);
            if (!q.getStatus().equals("new")) {
                // This is not supposed to happen, because client will only offer option to edit a question in the
                // "new" status. However if we have 2 admins logged in, and one posts while the other editing...
                throw new ForbiddenException();
            }
            em.getTransaction().begin();
            q.setText(patchQuestion.getText());
            for (ResponseOption opt: q.getPossibleResponses()) {
                em.remove(opt);
            }
            q.clearResponseOptions();
            for (ResponseOption opt : patchQuestion.getPossibleResponses()) {
                ResponseOption newOpt = new ResponseOption(opt.getText());
                em.persist(newOpt);
                q.addResponseOption(newOpt);
            }
            em.getTransaction().commit();
            return new ResponseEntity(q, HttpStatus.OK);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return new  ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        finally {
            em.close();
        }
    }
/*
    If it's not yet posted, we can post it (open it up for polling)
    TODO: in the client, make sure not posting a question with fewer than 2 response options.
 */
    @PatchMapping("/questions/{quid}/post")
    public synchronized ResponseEntity postQuestion(@RequestHeader HttpHeaders headers,
                                        @RequestBody Question patchQuestion, @PathVariable long quid) {
        loginManager.validateAdminUser(headers);
        EntityManager em = emf.createEntityManager();
        try {
            Question q = em.find(Question.class, quid);
            if (!q.getStatus().equals("new")) {
                // This is not supposed to happen, because client will only offer option to post a question in the
                // "new" status. However if we have 2 admins logged in, and both post at the same time.
                throw new ForbiddenException();
            }
            em.getTransaction().begin();
            q.post();
            em.getTransaction().commit();
            return new ResponseEntity(q, HttpStatus.OK);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return new  ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        finally {
            em.close();
        }
    }

    /*
    If it is posted, we can close it (disallow any additional voting)
    TODO: expose the close functionality in the admin's side of the client
     */
    @PatchMapping("/questions/{quid}/close")
    public synchronized ResponseEntity closeQuestion(@RequestHeader HttpHeaders headers,
                                       @RequestBody Question patchQuestion, @PathVariable long quid) {
        loginManager.validateAdminUser(headers);
        EntityManager em = emf.createEntityManager();
        try {
            Question q = em.find(Question.class, quid);
            if (!q.getStatus().equals("polling")) {
                // This is not supposed to happen, because client will only offer option to close a question in the
                // "polling" status. However if we have 2 admins logged in, and both act at the same time...
                throw new ForbiddenException();
            }
            em.getTransaction().begin();
            q.close();
            em.getTransaction().commit();
            return new ResponseEntity(q, HttpStatus.OK);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return new  ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        finally {
            em.close();
        }
    }

    /*
    If it has never been offered up for polling, we can delete a question.
    TODO: expose the delete functionality in the admin's side of the client
     */
    @DeleteMapping("/questions/{quid}/delete")
    public synchronized ResponseEntity deleteQuestion(@RequestHeader HttpHeaders headers, @PathVariable long quid) {
        loginManager.validateAdminUser(headers);
        EntityManager em = emf.createEntityManager();
        try {
            Question q = em.find(Question.class, quid);
            if (!q.getStatus().equals("new")) {
                // This is not supposed to happen, because client will only offer option to delete a question in the
                // "new" status. However if we have 2 admins logged in, and both act at the same time...
                throw new ForbiddenException();
            }
            em.getTransaction().begin();
            em.remove(q);
            // TODO: is deletion cascaded to the response options?
            em.getTransaction().commit();
            return new ResponseEntity(q, HttpStatus.OK);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return new  ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        finally {
            em.close();
        }
    }

}
