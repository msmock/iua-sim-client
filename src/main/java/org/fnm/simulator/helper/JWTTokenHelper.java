package org.fnm.simulator.helper;

import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.JsonParser;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;

import java.text.ParseException;
import java.util.Base64;

public class JWTTokenHelper {

    /**
     * Used in evaluation of the AuthZ Server's response. Extracts and returns the payload from
     * a given JSON Web Token (JWT).
     *
     * @param token the JWT as a string.
     * @return the decoded payload as a JSON string.
     */
    public String getPayload(String token) {
        String decoded = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        return JsonParser.parseString(decoded).getAsJsonObject().toString();
    }

    /**
     * Used in signature verification of the AuthZ Server's response. Extracts and returns the algorithm name
     * from the given JSON Web Token (JWT).
     *
     * @param token the JWT as a string.
     * @return the algorithm name specified in the JWT header as a string.
     */
    public String getAlgName(String token) {
        String decoded = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]));
        return JsonParser.parseString(decoded).getAsJsonObject().get("alg").getAsString();
    }

    /**
     * Used in signature verification of the AuthZ Server's response. Creates an Elliptic Curve Algorithm instance
     * from a public key configured for this test.
     *
     * @param key the public key as a string.
     * @return an {@code Algorithm} instance configured with the ECDSA-256 algorithm and the parsed public key.
     * @throws ParseException if the JWK content cannot be properly parsed.
     * @throws JOSEException  if an error occurs during the conversion or processing of the JWK.
     */
    public Algorithm getECPublicAlg(String key) throws ParseException, JOSEException {
        ECKey publicKey = JWK.parse(key).toECKey();
        return Algorithm.ECDSA256(publicKey.toECPublicKey());
    }

    /**
     * Used in signature verification of the AuthZ Server's response. Creates an RSA Algorithm instance
     * from from a public key configured for this test.
     *
     * @param key the public key as a string.
     * @return an {@code Algorithm} instance configured with the RSA-256 algorithm and the parsed public key.
     * @throws ParseException if the JWK content cannot be properly parsed.
     * @throws JOSEException  if an error occurs during the conversion or processing of the JWK.
     */
    public Algorithm getRSAPublicAlg(String key) throws ParseException, JOSEException {
        RSAKey publicKey = JWK.parse(key).toRSAKey();
        return Algorithm.RSA256(publicKey.toRSAPublicKey());
    }
}
