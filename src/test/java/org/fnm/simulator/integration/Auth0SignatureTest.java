package org.fnm.simulator.integration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.JsonObject;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;

import io.quarkus.test.junit.QuarkusTest;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Test for signing and verifying JSON Web Token with the auth0 library, used for educational purposes.
 */
@QuarkusTest
public class Auth0SignatureTest {

    private static final Logger LOG = Logger.getLogger(Auth0SignatureTest.class);

    /**
     * Tests the signing and verification of a JSON Web Token (JWT) with ECDSA keys.
     *
     * @throws IOException if there is an issue reading key files.
     * @throws ParseException if an error occurs parsing the JWT or key files.
     * @throws JOSEException if there is an error in creating or verifying the signature.
     * @throws NoSuchAlgorithmException if the specified algorithm cannot be found.
     */
    @Test
    void testSigning() throws IOException, ParseException, JOSEException, NoSuchAlgorithmException {

        String jwt = signJwt();
        DecodedJWT result = verifyJWT(jwt);

        String tokenPayload = new String(Base64.getUrlDecoder().decode(result.getPayload()));
        LOG.info("Token payload is: " + tokenPayload);
    }

    /**
     * Verifies a given JSON Web Token (JWT) utilizing an ECDSA public key loaded from a file.
     *
     * @param jwt the JWT string to be verified
     * @return the decoded JWT object containing token claims and other metadata
     * @throws IOException if there is an issue reading the public key file
     * @throws ParseException if an error occurs while parsing the public key or JWT
     * @throws JOSEException if there is an error processing the key or verifying the signature
     */
    private static DecodedJWT verifyJWT(String jwt) throws IOException, ParseException, JOSEException {

        // get the public key from file
        String publicKeyAsString = Files.readString(Paths.get("signature-keys/JWK-EC-public-key.json"));
        JWK publicJWK = JWK.parse(publicKeyAsString);

        ECKey publicKey = publicJWK.toECKey();

        Algorithm algorithm = Algorithm.ECDSA256(publicKey.toECPublicKey());

        JWTVerifier verifier = JWT.require(algorithm)
                .acceptLeeway(1)   // 1 sec for nbf and iat
                .acceptExpiresAt(5)   //5 secs for exp
                .build();

        return verifier.verify(jwt);
    }

    /**
     * Generates a signed JSON Web Token (JWT) using an ECDSA private key.
     *
     * @return the signed JWT as a String
     * @throws IOException if there is an issue reading the key file
     * @throws ParseException if an error occurs parsing the key file
     * @throws JOSEException if there is an error in creating the signature
     */
    private static String signJwt() throws IOException, ParseException, JOSEException {

        String signingKeyAsString = Files.readString(Paths.get("signature-keys/JWK-EC-pair.json"));
        JWK keyPair = JWK.parse(signingKeyAsString);

        // get the private key from the key pair
        ECKey ecKey = keyPair.toECKey();
        Algorithm algorithm = Algorithm.ECDSA256(ecKey.toECPrivateKey());

        JsonObject payload = getJWTPayload();

        // finally create the JWT
        String jwt = JWT.create().withPayload(payload.toString()).sign(algorithm);

        LOG.info("JWT: " + jwt);
        return jwt;
    }


    private static JsonObject getJWTPayload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("iss", "IUATestServer");
        payload.addProperty("sub", "${entity-client-id}");
        payload.addProperty("aud", "http://ehr.ch");
        payload.addProperty("iat", Instant.now().getEpochSecond());
        payload.addProperty("nbf", Instant.now().getEpochSecond());
        payload.addProperty("exp", Instant.now().getEpochSecond() + 300);
        payload.addProperty("jti", UUID.randomUUID().toString());
        return payload;
    }

}
