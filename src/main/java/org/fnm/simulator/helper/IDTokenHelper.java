package org.fnm.simulator.helper;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.JsonObject;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;

import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;

public class IDTokenHelper {

    /**
     * @param clientId the client ID to be included in the ID Token
     * @return the ID Token as string as it would be returned by the OIDC server
     */
    public String buildIdToken(String clientId) throws ParseException, IOException, JOSEException {
        Algorithm algorithm = rsaPrivateKeyAlgorithm();
        JsonObject payload = buildIDTokenPayload(clientId);
        return JWT.create().withPayload(payload.toString()).sign(algorithm);
    }

    /**
     * Return the algorithm to sign the ID Token.
     */
    private Algorithm rsaPrivateKeyAlgorithm() throws ParseException, JOSEException {
        RSAKey keyPair = JWK.parse(SigningKeyHelper.getRsaKeyPair()).toRSAKey();
        return Algorithm.RSA256(keyPair.toRSAPrivateKey());
    }

    /**
     * Builds the ID token payload.
     *
     * @param clientId the client ID to be included in the payload
     * @return ID token payload as JSON
     */
    public JsonObject buildIDTokenPayload(String clientId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("iss", "http://client-simulator.org");
        payload.addProperty("sub", "Bearer");
        payload.addProperty("aud", clientId);
        payload.addProperty("exp", Instant.now().getEpochSecond() + 600);
        payload.addProperty("iat", Instant.now().getEpochSecond());
        payload.addProperty("nonce", "n-0S6_WzA2Mj");
        payload.addProperty("name", "Martina Mustermann");
        return payload;
    }

}
