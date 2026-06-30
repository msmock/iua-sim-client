package org.fnm.simulator.simulations.clientCredentials;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.authlete.hms.SigningInfo;
import com.authlete.hms.fapi.FapiResourceRequestSigner;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;

import net.ihe.gazelle.simulation.business.callback.*;

import org.apache.commons.codec.digest.DigestUtils;
import org.fnm.simulator.helper.GrantType;
import org.fnm.simulator.helper.JWTTokenHelper;
import org.fnm.simulator.helper.SigningKeyHelper;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an action that performs a client credential flow in compliance with the CH:IUA standard.
 */
public class TokenRequestAction {

    private static final Logger LOG = Logger.getLogger(TokenRequestAction.class);

    // the simulation parameters
    private final ClientCredentialConfig config;

    // the java net http client
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final JWTTokenHelper jwtTokenHelper = new JWTTokenHelper();

    /**
     * Constructor with configuration parameters.
     *
     * @param config the configuration parameters for the client credential flow.
     */
    public TokenRequestAction(ClientCredentialConfig config) {
        this.config = config;
    }

    /**
     * @return action result indicating success or failure of the test
     */
    public TransactionReport run() {

        LOG.info("Perform post request to authZ server");

        // put client_id and client_secret in the Authentication header
        String authHeader = buildAuthHeader(config.clientId, config.clientSecret);

        // add the other parameter to the body
        Map<String, String> bodyElements = new LinkedHashMap<>();
        bodyElements.put("grant_type", GrantType.clientCredentials);
        bodyElements.put("principal", config.principal);
        bodyElements.put("principal_id", config.principalId);
        bodyElements.put("scope", config.scope);

        if (config.personId != null && !config.personId.isBlank())
            bodyElements.put("person_id", config.personId);

        String requestBody = formEncode(bodyElements);

        // add digest header for http signature
        String contentDigestHeader = "sha-512=:" + Base64.getEncoder().encodeToString(
                DigestUtils.sha512(requestBody)
        ) + ":";

        // get the key pair for http signature
        JWK signingKey;
        try {
            signingKey = SigningKeyHelper.getEcKeyPairJWK();
        } catch (ParseException e) {
            String message = "Exception from parsing JWK file";
            LOG.error(message, e);
            return getUndefinedTransactionReport(message);
        }

        FapiResourceRequestSigner signer = new FapiResourceRequestSigner()
                .setMethod("POST")
                .setTargetUri(URI.create(config.tokenEndpointUrl))
                .setAuthorization(authHeader)
                .setContentDigest(contentDigestHeader)
                .setSigningKey(signingKey)
                .setCreated(Instant.now());

        SigningInfo signingInfo;
        try {
            signingInfo = signer.sign();
        } catch (SignatureException e) {
            String message = "Unable to create the http signature.";
            LOG.error(message, e);
            return getUndefinedTransactionReport(message);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(config.tokenEndpointUrl))
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", authHeader)
                .header("Content-Digest", contentDigestHeader)
                .header("Signature-Input", "sig1=" + signingInfo.getSerializedSignatureMetadata())
                .header("Signature", "sig1=" + signingInfo.getSerializedSignature())
                .timeout(Duration.ofSeconds(config.timeoutInSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response;

        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            String message = "IO error: Could not connect to authZ server at " + config.tokenEndpointUrl;
            LOG.error(message, e);
            return getFailedTransactionReport(message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String message = "IO error: Request interrupted while connecting to authZ server at " + config.tokenEndpointUrl;
            LOG.error(message, e);
            return getFailedTransactionReport(message);
        }

        int statusCode = response.statusCode();
        String reasonPhrase = statusCode == 200 ? "OK" : "HTTP " + statusCode;

        LOG.info("Status: " + statusCode + " " + reasonPhrase);
        if (statusCode != 200) {
            String message = "Error: AuthZ Server returned " + reasonPhrase;
            LOG.error(message);
            return getFailedTransactionReport(message);
        }

        String responseBody = response.body();
        LOG.debug("Received responseBody from server :" + responseBody);

        String algName = jwtTokenHelper.getAlgName(responseBody);
        LOG.info("Algorithm name in responseBody is : " + algName);

        String responsePayload = jwtTokenHelper.getPayload(responseBody);
        LOG.info("Payload in responseBody is : " + responsePayload);

        try {

            Algorithm algorithm;

            switch (algName) {
                case "RS256" -> algorithm = jwtTokenHelper.getRSAPublicAlg(config.jwtPublicKey);
                case "ES256" -> algorithm = jwtTokenHelper.getECPublicAlg(config.jwtPublicKey);
                case "HS256" -> algorithm = Algorithm.HMAC256("secret");
                default -> {
                    String message = "Unsupported algorithm : " + algName;
                    LOG.error(message);
                    return getFailedTransactionReport(message);
                }
            }

            // verify the jwt signature
            JWTVerifier verifier = JWT.require(algorithm)
                    .acceptLeeway(1)
                    .acceptExpiresAt(5)
                    .build();
            DecodedJWT jwt = verifier.verify(responseBody);

            String tokenPayload = new String(Base64.getUrlDecoder().decode(jwt.getPayload()));
            LOG.info("Token payload is: " + tokenPayload);

            // create the transaction report
            TransactionReport report = new TransactionReport();
            report.setResult(Result.PASSED);
            report.setStandards(List.of("CH:ITI-71", "HTTP/1.1"));
            report.setInitiator(config.initiator);
            report.setResponder(config.responder);
            report.setTransaction("CH:IUA Client Credential Flow [ITI-71]");
            report.setStandards(List.of("CH:IUA"));

            Message responseMessage = new Message();
            responseMessage.setName("Get Access Token Response");
            responseMessage.setContent(responseBody.getBytes(StandardCharsets.UTF_8));
            responseMessage.setDateTime(Instant.now());
            responseMessage.setSender(config.initiator.getName());
            responseMessage.setReceiver(config.responder.getName());
            report.setMessages(List.of(responseMessage));

            report.setNote("The JWT token is valid.");
            return report;

        } catch (IllegalStateException e) {

            String message = "Unknown algorithm name " + algName;
            LOG.error(message, e);
            return getFailedTransactionReport(message);

        } catch (JOSEException | ParseException e) {

            String message = "Exception from JOSE parsing for algorithm " + algName;
            LOG.error(message, e);
            return getFailedTransactionReport(message);

        } catch (SignatureVerificationException e) {
            String message = "Verification of the signature of the JWT responded from server failed.";
            LOG.error(message, e);
            return getFailedTransactionReport(message);
        }
    }

    /**
     * @param bodyElements map of key value pairs to be encoded in the request body
     * @return the encoded body as a string
     */
    private String formEncode(Map<String, String> bodyElements) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : bodyElements.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append("&");
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            builder.append("=");
            builder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    /**
     * Build the Authorization header for the client credential flow.
     *
     * @return encoded authorization header with content clientId:clientSecret
     */
    private String buildAuthHeader(String clientId, String clientSecret) {
        String credentials = clientId + ":" + clientSecret;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
        return "Basic " + encodedCredentials;
    }

    /**
     * @return a transaction report indicating a failed test
     */
    private TransactionReport getFailedTransactionReport(String message) {
        TransactionReport report = new TransactionReport();
        report.setResult(Result.FAILED);
        report.setInitiator(config.initiator);
        report.setResponder(config.responder);
        report.setNote(message);
        return report;
    }

    /**
     * @return a transaction report indicating an undefined test result caused by an error the simulator code.
     */
    private TransactionReport getUndefinedTransactionReport(String message) {
        TransactionReport report = new TransactionReport();
        report.setResult(Result.UNDEFINED);
        report.setInitiator(config.initiator);
        report.setResponder(config.responder);
        report.setNote(message);
        return report;
    }

}
