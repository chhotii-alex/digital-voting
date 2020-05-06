package com.jagbag.dvoting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
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
            Voter adminUser = getForUsername("admin");
            if (adminUser == null) {
                adminUser = new Voter("admin", "admin", "admin@localhost");
                adminUser.setPassword("changeme!");
                adminUser.setEmailConfirmed(true);
            }
            adminUser.setAdmin(true);
            EntityManager em = emf.createEntityManager();
            em.getTransaction().begin();
            try {
                em.merge(adminUser);
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
            v = em.merge(v);
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
                System.out.println("Threw exception when committing changes wrought by confirmEmail");
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

    /**
     * Query for a Voter with this email address, or this is the old email address
     * @param email
     * @return a Voter with matching email or old email, or null
     */
    public Voter getForEmail(String email) {
        EntityManager em = emf.createEntityManager();
        Voter v = null;
        String hql = "select v from Voter v where v.email like :email";
        Query query = em.createQuery(hql);
        query.setParameter("email", email);
        List results = query.getResultList();
        if (results.size() > 0) {
            v = (Voter)(results.get(0));
        }
        if (v == null) {
            hql = "select v from Voter v where v.oldEmail like :email";
            query = em.createQuery(hql);
            query.setParameter("email", email);
            results = query.getResultList();
            if (results.size() > 0) {
                v = (Voter)(results.get(0));
            }
        }
        em.close();
        return v;
    }

    /**
     * Handle a request that a password get reset.
     * @see Voter#prepareForReset() for how the request is handled, which involves valuing some persistent fields.
     * We handle persisting the changes to the database in this method.
     * @param v -- user with an account needing a password reset
     * @return whether or not we were able to update the database with the info about the new password
     */
    public boolean prepareForReset(Voter v) {
        boolean success = true;
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        try {
            v.prepareForReset();
            em.merge(v);
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

    /**
     * Ask that a Voter object switch over to the random new password, and persist the change.
     * @param v
     */
    public boolean resetPassword(Voter v, String code) {
        boolean success = true;
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        try {
            v.confirmPasswordReset(code);
            em.merge(v);
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

    public boolean setPassword(Voter v, String newPassword) {
        boolean success = true;
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        try {
            v.setPassword(newPassword);
            em.merge(v);
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

    public boolean updateVoter(Voter v) {
        boolean success = true;
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        try {
            em.merge(v);
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
}
