package org.fnm.simulator.ressource;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.fnm.simulator.helper.IDTokenHelper;
import org.fnm.simulator.helper.SigningKeyHelper;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.util.UUID;

@Path("/mock")
public class OIDCTokenMock {

    private static final Logger LOG = Logger.getLogger(OIDCTokenMock.class);

    private final IDTokenHelper idTokenHelper = new IDTokenHelper();

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
        return Response.ok(idTokenHelper.buildIdToken(clientId)).build();
    }

    @GET
    @Path("oidc-id-token-payload")
    @Produces("application/json")
    public Response getIDTokenPayload(@QueryParam("client_id") String clientId) throws ParseException, IOException, JOSEException {
        LOG.info("OIDC Token Mock got id token payload request for client_id: " + clientId);
        Gson gson = new Gson();
        return Response.ok(gson.toJson(idTokenHelper.buildIDTokenPayload(clientId))).build();
    }

    @GET
    @Path("oidc-id-token-jwk")
    @Produces("application/json")
    public Response getIDTokenJWK() throws ParseException, IOException, JOSEException {
        return Response.ok(rsaPublicKey()).build(); // Output the public RSA JWK parameters only
    }

    /**
     * Builds the OIDC response payload.
     * @param clientId the client ID to be included in the ID Token
     * @return token payload as JSON
     */
    private JsonObject buildResponsePayload(String clientId) throws ParseException, IOException, JOSEException {
        JsonObject payload = new JsonObject();
        payload.addProperty("access_token", UUID.randomUUID().toString());
        payload.addProperty("token_type", "Bearer");
        payload.addProperty("expires_in", Instant.now().getEpochSecond() + 600);
        payload.addProperty("id_token", idTokenHelper.buildIdToken(clientId));
        return payload;
    }

    private RSAKey rsaPublicKey() throws ParseException, JOSEException {
        RSAKey keyPair = JWK.parse(SigningKeyHelper.getRsaKeyPair()).toRSAKey();
        return keyPair.toPublicJWK();
    }


}
