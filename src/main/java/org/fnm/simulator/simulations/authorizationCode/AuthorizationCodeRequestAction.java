package org.fnm.simulator.simulations.authorizationCode;

import net.ihe.gazelle.simulation.business.callback.Result;
import net.ihe.gazelle.simulation.business.callback.TransactionReport;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AuthorizationCodeRequestAction {


    private static final Logger LOG = Logger.getLogger(AuthorizationCodeRequestAction.class);

    private final AuthorizationCodeConfig config;

    // the java net http client
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public AuthorizationCodeRequestAction(AuthorizationCodeConfig config) {
        this.config = config;
    }

    /**
     * Run the simulation.
     *
     * @return a TransactionReport object indicating the result of the test.
     */
    public TransactionReport run() {

        // put client_id and client_secret in the Authentication header
        String authHeader = buildAuthHeader(config.clientId, config.clientSecret);

        // URLencode the scope value.
        String queryParameter = "response_type=code&state=123456789&redirect_uri=http://localhost:9000/callback&scope=" +
                URLEncoder.encode(config.scope, StandardCharsets.UTF_8);

        // URI uri = URI.create(config.codeEndpointUrl+"?"+URLEncoder.encode(queryParameter, StandardCharsets.UTF_8));
        URI uri = URI.create(config.codeEndpointUrl + "?" + queryParameter);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(uri)
                .header("Cache-Control", "no-cache")
                .header("Authorization", authHeader)
                .timeout(Duration.ofSeconds(config.timeoutInSeconds))
                .GET()
                .build();

        HttpResponse<String> response;

        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            String message = "IO error: Could not connect to authZ server at " + config.codeEndpointUrl;
            LOG.error(message, e);
            return getFailedTransactionReport(message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String message = "IO error: Request interrupted while connecting to authZ server at " + config.codeEndpointUrl;
            LOG.error(message, e);
            return getFailedTransactionReport(message);
        }

        int statusCode = response.statusCode();
        List<Integer> redirectCodes = List.of(301, 302, 303, 307, 308);
        String reasonPhrase = redirectCodes.contains(statusCode) ? "OK" : "HTTP " + statusCode;

        LOG.info("Status: " + statusCode + " " + reasonPhrase);

        if (!redirectCodes.contains(statusCode)) {
            String message = "Error: AuthZ Server returned " + reasonPhrase;
            LOG.error(message);
            return getFailedTransactionReport(message);
        }

        // from header get the location of the redirect message which contains the code
        String location = response.headers()
                .firstValue("location")
                .orElse(null);

        if (location == null || location.isBlank()) {
            String message = "Error: Redirect response did not contain a Location header";
            LOG.error(message);
            return getFailedTransactionReport(message);
        }

        URI locationUri = URI.create(location);

        Map<String, String> queryParameters = locationUri.getQuery()
                .lines()
                .flatMap(query -> Stream.of(query.split("&")))
                .map(parameter -> parameter.split("=", 2))
                .collect(Collectors.toMap(
                        parameter -> URLDecoder.decode(parameter[0], StandardCharsets.UTF_8),
                        parameter -> parameter.length > 1
                                ? URLDecoder.decode(parameter[1], StandardCharsets.UTF_8)
                                : ""
                ));

        String authorizationCode = queryParameters.get("code");
        String state = queryParameters.get("state");

        if (authorizationCode == null || authorizationCode.isBlank()) {
            String message = "Error: Redirect Location did not contain an authorization code";
            LOG.error(message);
            return getFailedTransactionReport(message);
        }

        if (state == null || state.isBlank()) {
            String message = "Error: Redirect Location did not contain a state";
            LOG.error(message);
            return getFailedTransactionReport(message);
        }

        LOG.info("Authorization code received: " + authorizationCode);
        LOG.info("State received: " + state);

        // TODO return the code and state to the simulation
        TransactionReport report = new TransactionReport();
        report.setResult(Result.PASSED);
        report.setStandards(List.of("CH:ITI-71", "HTTP/1.1"));
        report.setInitiator(config.initiator);
        report.setResponder(config.responder);
        report.setTransaction("CH:IUA Authorization Code Flow [ITI-71]");
        report.setStandards(List.of("CH:IUA"));

        return report;
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
        report.setStandards(List.of("CH:ITI-71", "HTTP/1.1"));
        report.setTransaction("CH:IUA Authorization Code Flow [ITI-71]");
        report.setStandards(List.of("CH:IUA"));
        report.setNote(message);
        return report;
    }

}
