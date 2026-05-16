package org.fnm.simulator.helper;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.fnm.simulator.IUAClientSimulationService;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Instant;
import java.util.UUID;

@Path("/mock")
public class OIDCTokenProviderMock {

    private static final Logger LOG = Logger.getLogger(OIDCTokenProviderMock.class);

    @Inject
    IUAClientSimulationService simulationService;

    @GET
    @Path("oidc-reponse")
    @Produces("application/json")
    public Response getResponse(@QueryParam("client_id") String clientId) throws ParseException, IOException, JOSEException {
        LOG.info("OIDC Token Mock got OIDC response request for client_id: " + clientId);
        Gson gson = new Gson();
        return Response.ok(gson.toJson(buildResponsePayload(clientId))).build();
    }

    @GET
    @Path("oidc-id-token")
    @Produces("text/plain")
    public Response getIDToken(@QueryParam("client_id") String clientId) throws ParseException, IOException, JOSEException {
        LOG.info("OIDC Token Mock got id token request for client_id: " + clientId);
        return Response.ok(buildIdToken(clientId)).build();
    }

    @GET
    @Path("oidc-id-token-payload")
    @Produces("application/json")
    public Response getIDTokenPayload(@QueryParam("client_id") String clientId) throws ParseException, IOException, JOSEException {
        LOG.info("OIDC Token Mock got id token payload request for client_id: " + clientId);
        Gson gson = new Gson();
        return Response.ok(gson.toJson(buildIDTokenPayload(clientId))).build();
    }

    @GET
    @Path("oidc-id-token-jwk")
    @Produces("application/json")
    public Response getIDTokenJWK() throws ParseException, IOException, JOSEException {
        RSAKey jwk = simulationService.getOidcIdTokenSigningKeyPair();
        return Response.ok(jwk.toPublicJWK()).build(); // Output the public RSA JWK parameters only
    }

    /**
     * @return token payload as JSON
     */
    private JsonObject buildResponsePayload(String clientId) throws ParseException, IOException, JOSEException {
        JsonObject payload = new JsonObject();
        payload.addProperty("access_token", UUID.randomUUID().toString());
        payload.addProperty("token_type", "Bearer");
        payload.addProperty("expires_in", Instant.now().getEpochSecond() + 600);
        payload.addProperty("id_token", buildIdToken(clientId));
        return payload;
    }

    /**
     * @return the ID Token as string
     */
    private String buildIdToken(String clientId) throws ParseException, IOException, JOSEException {
        Algorithm algorithm = loadRSAPrivateKey();
        JsonObject payload = buildIDTokenPayload(clientId);
        return JWT.create().withPayload(payload.toString()).sign(algorithm);
    }

    /**
     * Builds the ID token payload.
     *
     * @param clientId the client ID to be included in the payload
     * @return ID token payload as JSON
     */
    private JsonObject buildIDTokenPayload(String clientId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("iss", "http://client-simulator.org");
        payload.addProperty("sub", "Bearer");
        payload.addProperty("aud", clientId);
        payload.addProperty("exp", Instant.now().getEpochSecond() + 600);
        payload.addProperty("iat", Instant.now().getEpochSecond());
        payload.addProperty("nonce", "n-0S6_WzA2Mj");
        payload.addProperty("name", "Jane Doe");
        return payload;
    }

    public Algorithm loadECPrivateKey() throws ParseException, IOException, JOSEException {
        String privateKeyAsString = Files.readString(Paths.get("signature-keys/JWK-EC-pair.json"));
        JWK privateJWK = JWK.parse(privateKeyAsString);
        ECKey privateKey = privateJWK.toECKey();
        return Algorithm.ECDSA256(privateKey.toECPrivateKey());
    }

    public Algorithm loadRSAPrivateKey() throws ParseException, IOException, JOSEException {
        String privateKeyAsString = Files.readString(Paths.get("signature-keys/JWK-RSA-pair.json"));
        JWK privateJWK = JWK.parse(privateKeyAsString);
        RSAKey privateKey = privateJWK.toRSAKey();
        return Algorithm.RSA256(privateKey.toRSAPrivateKey());
    }

}
