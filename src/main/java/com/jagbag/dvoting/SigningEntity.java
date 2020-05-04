package com.jagbag.dvoting;

import com.fasterxml.jackson.annotation.JsonGetter;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

public class SigningEntity {
    protected KeyPair rsaKeys;

    /*
Note, there is no storage of keys. Keys are only kept in memory.
This means that if the service is re-started, there is no persistence of a key from one
run to the next.
This means that the CTF cannot confirm signatures on ballots that were signed in a previous run.
If a poll is started, and voters have their ballots signed, and then the service is re-started, all the
existing signed ballots are invalidated. We can't have the voters who haven't voted get their ballots re-signed,
because we do not know who has and hasn't voted-- thus, if we allow re-signing ballots after a restart, voters who
voted before the re-start would have the opportunity to vote twice. Without any storage of the key, upon start-up,
we need to close polling on all questions, and the only way to proceed would be to start a fresh round of polling
by posing the same question again as a new question.
This is why we have StartupActions value the pollCloseWhen field on any open questions found in the database
upon starting.
 */
    protected static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, new SecureRandom());
        KeyPair pair = generator.generateKeyPair();

        return pair;
    }

    protected void initializeKeys() throws NoSuchAlgorithmException {
        rsaKeys = generateKeyPair();
    }
    protected RSAPublicKey getPublicKey() {
        if (rsaKeys == null) return null;
        return (RSAPublicKey) (rsaKeys.getPublic());
    }

    protected BigInteger getPrivateExponent() {
        if (rsaKeys == null) return null;
        return ((RSAPrivateKey)rsaKeys.getPrivate()).getPrivateExponent();
    }

    /**
     * Get the modulus for the RSA key pair used for signing chits.
     */
    public BigInteger getModulus() {
        if (rsaKeys == null) return null;
        return getPublicKey().getModulus();
    }

    /**
     * Get the public key of the RSA key pair used for response signing chits.
     * The public key and modulus are sent to the client, and can be shouted from the rooftops.
     * Note the lack of any public method exposing the private key, however.
     */
    public BigInteger getPublicExponent() {
        if (rsaKeys == null) return null;
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

    public String signText(String blindedMessageText) {
        if (rsaKeys == null) {
            throw new RuntimeException("Asked to sign when not  ready to sign.");
        }
        BigInteger t = decoded(blindedMessageText);
        RSAPrivateKey key = (RSAPrivateKey)(rsaKeys.getPrivate());
        BigInteger d = key.getPrivateExponent();
        BigInteger n = key.getModulus();
        BigInteger signedT = t.modPow(d, n);  // sign by encrypting with private key
        String signedAsString = encoded(signedT);
        return signedAsString;
    }

    protected boolean confirmSignature(String chit, String signedChit) {
        if (rsaKeys == null) {
            throw new RuntimeException("How can we be confirming signatures when not even ready to sign?");
        }
        BigInteger m = new BigInteger(chit.getBytes());
        BigInteger s = decoded(signedChit);
        BigInteger alleged = s.modPow(getPublicExponent(), getModulus());
        return (alleged.equals(m));
    }

}