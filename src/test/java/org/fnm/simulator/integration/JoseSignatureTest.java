package org.fnm.simulator.integration;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.*;

import io.quarkus.test.junit.QuarkusTest;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.interfaces.RSAPrivateKey;
import java.text.ParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for signing and verifying JSON Web Token with the JOSE library, used for educational purposes.
 */
@QuarkusTest
public class JoseSignatureTest {

    private static final Logger LOG = Logger.getLogger(JoseSignatureTest.class);

    /**
     * Tests the signing and verification of a JSON Web Signature (JWS).
     *
     * @throws JOSEException if there is an error with the JWS signing or verification process.
     * @throws ParseException if the JWS object could not be parsed.
     */
    @Test
    void testSigning() throws JOSEException, ParseException {

        RSAKey rsaJWK = new RSAKeyGenerator(2048).keyID("123").generate();

        JWSSigner signer = new RSASSASigner(rsaJWK);

        JWSObject jwsObject = new JWSObject(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJWK.getKeyID()).build(),
                new Payload("In RSA we trust!"));

        // Compute the RSA signature
        jwsObject.sign(signer);

        // To serialize to compact form
        String s = jwsObject.serialize();

        // parse the JWS and verify it, e.g., on client-side
        jwsObject = JWSObject.parse(s);

        RSAKey rsaPublicJWK = rsaJWK.toPublicJWK();
        JWSVerifier verifier = new RSASSAVerifier(rsaPublicJWK);

        assertTrue(jwsObject.verify(verifier));

        assertEquals("In RSA we trust!", jwsObject.getPayload().toString());

    }

    /**
     * Tests the signing and verification process of a JSON Web Signature (JWS) using RSA.
     *
     * @throws JOSEException if there is an error with the signing or verification process.
     * @throws ParseException if there is an error in parsing JWKs or JWS objects.
     * @throws IOException if there is an issue reading the key files.
     */
    @Test
    void testRSASigning() throws JOSEException, ParseException, IOException {

        String signingKeyAsString = Files.readString(Paths.get("signature-keys/JWK-RSA-pair.json"));
        JWK signingKey = JWK.parse(signingKeyAsString);

        RSAKey rsaKey = signingKey.toRSAKey();

        RSAPrivateKey rsaPrivateKey = rsaKey.toRSAPrivateKey();
        JWSSigner signer = new RSASSASigner(rsaPrivateKey);

        JWSObject jwsObject = new JWSObject(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                new Payload("In RSA we trust!"));

        // Sign the object
        jwsObject.sign(signer);

        // read public key
        String publicKeyAsString = Files.readString(Paths.get("signature-keys/JWK-RSA-public-key.json"));
        RSAKey parsed = RSAKey.parse(publicKeyAsString);

        JWSVerifier verifier = new RSASSAVerifier(parsed);
        boolean verify = jwsObject.verify(verifier);

        assertTrue(verify);

        assertEquals("In RSA we trust!", jwsObject.getPayload().toString());

        LOG.info("JWS Object is: " + jwsObject.serialize());
    }

    /**
     * Tests the signing and verification of a JSON Web Signature (JWS) using ECDSA keys.
     *
     * @throws JOSEException if there is an error related to signing or verification of the JWS.
     * @throws ParseException if the JWK or JWS objects cannot be parsed.
     * @throws IOException if there is an issue reading the key files.
     */
    @Test
    void testECSigning() throws JOSEException, ParseException, IOException {

        String signingKeyAsString = Files.readString(Paths.get("signature-keys/JWK-EC-pair.json"));
        JWK signingKey = JWK.parse(signingKeyAsString);

        ECKey ecKey = signingKey.toECKey();
        JWSSigner signer = new ECDSASigner(ecKey);

        JWSObject jwsObject = new JWSObject(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(ecKey.getKeyID()).build(),
                new Payload("In EC we trust!"));

        // Sign the object
        jwsObject.sign(signer);

        // read public key
        String publicKeyAsString = Files.readString(Paths.get("signature-keys/JWK-EC-public-key.json"));
        ECKey parsed = ECKey.parse(publicKeyAsString);

        JWSVerifier verifier = new ECDSAVerifier(parsed);
        boolean verify = jwsObject.verify(verifier);

        assertTrue(verify);

        assertEquals("In EC we trust!", jwsObject.getPayload().toString());

        LOG.info("JWS Object is: " + jwsObject.serialize());

    }




}
