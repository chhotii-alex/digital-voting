package com.jagbag.dvoting;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.saml2.Saml2RelyingPartyProperties;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

class SigningEntityTest {

    @Test
    void getModulus() {
        SigningEntity thing = new SigningEntity();
        try {
            thing.initializeKeys();
        }
        catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            fail("NoSuchAlgorithmException thrown");
        }
        BigInteger  big = thing.getModulus();
        assertNotNull(big);
    }

    @Test
    void getPublicExponent() {
        SigningEntity thing = new SigningEntity();
        try {
            thing.initializeKeys();
        }
        catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            fail("NoSuchAlgorithmException thrown");
        }
        BigInteger  big = thing.getPublicExponent();
        assertNotNull(big);
    }

    @Test
    void testManyEncodings() {
        encodedDecodeTest(BigInteger.ZERO);
        encodedDecodeTest(BigInteger.ONE);
        encodedDecodeTest(BigInteger.TEN);
        encodedDecodeTest(new BigInteger("48572093864508260186508263582365876578125639457236952345"));
        encodedDecodeTest(BigInteger.probablePrime(40, ThreadLocalRandom.current()));
    }

    void encodedDecodeTest(BigInteger num) {
        String str = SigningEntity.encoded(num);
        BigInteger result = SigningEntity.decoded(str);
        assertEquals(num, result);
    }

    @Test
    void signAndConfirmTexts() {
        SigningEntity thing = new SigningEntity();
        try {
            thing.initializeKeys();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            fail("NoSuchAlgorithmException thrown");
        }
        signAndConfirmTest(thing, "I am the very important message");
        signAndConfirmTest(thing, "This is an ever so slightly longer message!!!");
        signAndConfirmTest(thing, "0234623623868903248560836452836402346018257012857036234067");
    }

    void signAndConfirmTest(SigningEntity thing, String chit) {
        BigInteger m = new BigInteger(chit.getBytes());
        String encodedText = SigningEntity.encoded(m);
        String signedText = thing.signText(encodedText);
        assertTrue(thing.confirmSignature(chit, signedText));
        assertFalse(thing.confirmSignature(chit, "random1other2string"));
    }

}