package com.jagbag.dvoting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Interface with the database store of user accounts. Allows looking up users by username, CRUD operations,
 * etc.
 * Note that when the service is first spun up with a fresh empty database, there's no way to add admin users,
 * because there are no admin users yet to grant anyone admin privilege. Thus, first thing we do, before anything,
 * we make sure at least one user exists with admin privilege. If not, we create a user with the username
 * "admin" and password "changeme!".
 */
@Component("voterListManager")
public class VoterListManager {
    @Autowired
    private EntityManagerFactory emf;

    private boolean initialized;

    public VoterListManager() {
        initialized = false;
    }

    public synchronized void initialize() throws UnsupportedEncodingException, NoSuchAlgorithmException {
        if (!initialized) {
            createAdminUserIfNeeded();
            initialized = true;
        }
    }

    protected synchronized void createAdminUserIfNeeded() throws UnsupportedEncodingException, NoSuchAlgorithmException {
        if (countAdminUsers() < 1) {
            Voter adminUser = new Voter("admin", "admin", "admin@localhost");
            adminUser.setAdmin(true);
            adminUser.setPassword("changeme!");
            adminUser.setEmailConfirmed(true);
            EntityManager em = emf.createEntityManager();
            em.getTransaction().begin();
            try {
                em.persist(adminUser);
                em.getTransaction().commit();
            }
            catch (Exception ex) {
                em.getTransaction().rollback();
                throw new RuntimeException("Cannot save to database");
            }
            finally {
                em.close();
            }
        }
    }

    protected synchronized int countAdminUsers() {
        int count;
        EntityManager em = emf.createEntityManager();
        String hql = "select v from Voter v where v.admin = true";
        List<Voter> list = em.createQuery(hql).getResultList();
        count = list.size();
        em.close();
        return count;
    }

    public Voter getForUsername(String username) {
        Voter result = null;
        EntityManager em = emf.createEntityManager();
        String hql = "select v from Voter v where v.username = :username";
        List<Voter> list = em.createQuery(hql).setParameter("username", username).getResultList();
        if (list.size() == 1) {
            result = list.get(0);
        }
        em.close();
        return result;
    }

    public Collection<Voter> voters() {
        EntityManager em = emf.createEntityManager();
        String hql = "select v from Voter v order by v.allowedToVote DESC, v.admin DESC, v.name";
        List<Voter> list = em.createQuery(hql).getResultList();
        em.close();
        return list;
    }

    public boolean addVoter(Voter v, String password) {
        boolean success = true;
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        try {
            v.setPassword(password);
            v.prepareForConfirmationEmail();
            em.persist(v);
            em.getTransaction().commit();
        }
        catch (Exception ex) {
            ex.printStackTrace();
            em.getTransaction().rollback();
            success = false;
        }
        finally {
            em.close();
        }
        return success;
    }

    public void removeVoter(Voter v) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        try {
            em.remove(v);
            em.getTransaction().commit();
        }
        catch (Exception ex) {
            // TODO: log this somehow
            ex.printStackTrace();
            em.getTransaction().rollback();
        }
        finally {
            em.close();
        }
    }

    public boolean confirmEmail(Voter v, String code) {
        if (v.canConfirmCode(code)) {
            boolean success = false;
            EntityManager em = emf.createEntityManager();
            em.getTransaction().begin();
            try {
                v.confirmEmail(code);
                em.merge(v);
                em.getTransaction().commit();
                success = true;
            }
            catch (Exception ex) {
                ex.printStackTrace();
                em.getTransaction().rollback();
            }
            finally {
                em.close();
            }
            return success;
        }
        else {
            return false;
        }
    }
}
