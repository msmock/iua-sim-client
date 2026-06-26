package org.fnm.simulator.simulations.authorizationCode;

import net.ihe.gazelle.simulation.business.callback.Result;
import net.ihe.gazelle.simulation.business.callback.TransactionReport;
import org.fnm.simulator.helper.GrantType;
import org.jboss.logging.Logger;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TokenRequestAction {

    private static final Logger LOG = Logger.getLogger(TokenRequestAction.class);

    private final AuthorizationCodeConfig config;

    public TokenRequestAction(AuthorizationCodeConfig config) {
        this.config = config;
    }

    public TransactionReport run() {

        LOG.info("Perform post request to authZ server");

        // put client_id and client_secret in the Authentication header
        String authHeader = buildAuthHeader(config.clientId, config.clientSecret);

        // add the other parameter to the body
        Map<String, String> bodyElements = new LinkedHashMap<>();
        bodyElements.put("grant_type", GrantType.clientCredentials);
        bodyElements.put("scope", config.scope);

        if (config.personId != null && !config.personId.isBlank())
            bodyElements.put("person_id", config.personId);

        if (config.principal != null && !config.principal.isBlank())
            bodyElements.put("principal", config.principal);

        if (config.principalId != null && !config.principalId.isBlank())
            bodyElements.put("principal_id", config.principalId);

        // TODO add other parameters

        // TODO Implement the token request




        // build and return the transaction report
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
