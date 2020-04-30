package com.jagbag.dvoting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Component
public class StartupActions implements ApplicationRunner {
    @Autowired
    private VoterListManager voterListManager;
    @Autowired
    protected EntityManagerFactory emf;

    public void run(ApplicationArguments args) {
        try {
            voterListManager.initialize();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        // Close any questions that were left open in the previous run:
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        String hql = "select q from Question q where postedWhen is not null and closedWhen is null";
        List<Question> list = em.createQuery(hql).getResultList();
        for (Question q : list) {
            q.close();
        }
        try {
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
