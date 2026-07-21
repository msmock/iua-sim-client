package org.fnm.simulator.simulations.authorizationCode;

import net.ihe.gazelle.simulation.business.callback.Role;
import net.ihe.gazelle.simulation.business.setup.*;
import org.fnm.simulator.helper.UserRole;
import org.jboss.logging.Logger;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Container for the simulation configuration and parameters.
 */
public class AuthorizationCodeConfig {

    private static final Logger LOG = Logger.getLogger(AuthorizationCodeConfig.class);

    // fixed for IUA client simulation
    public final Role initiator;
    public final Role responder;

    // parameter read from setup indicating the current test session
    public String sessionId;

    // the identity of the sequence supported by the simulation
    public String sequenceId;

    public long timeoutInSeconds;

    // parameters read from setup
    public List<Parameter> simulationParameters;

    // parameter read from setup
    public String codeEndpointUrl;
    public String tokenEndpointUrl;
    public String clientId;
    public String clientSecret;
    public String state;
    // public String redirectUri; // fixed for the simulator
    public String scope;
    public String personId;
    public String principal;
    public String principalId;
    public String group;
    public String groupId;
    public String resource;
    // public String codeChallenge; // currently not used
    // public String codeChallengeMethod; // currently not used
    public String requestedTokenType;

    public String jwtPublicKey;

    // result from the authorization code request
    public String authorizationCode;


    /**
     * Container for the simulation configuration parameters.
     * @param sessionId the current test session
     * @param simulationRequest the information required for a single simulation run
     */
    public AuthorizationCodeConfig(String sessionId, SimulationRequest simulationRequest) {

        this.sessionId = sessionId;
        this.sequenceId = simulationRequest.getSequenceId();
        this.simulationParameters = simulationRequest.getSimulationParameters();

        this.timeoutInSeconds = simulationRequest.getTimeoutSeconds();

        initiator = new Role();
        initiator.setName("IUA Client");
        initiator.setConfigs(List.of());
        initiator.setSimulated(true);

        responder = new Role();
        responder.setName("CH:IUA Server");
        responder.setConfigs(List.of());

        // parse parameters
        for (Parameter parameter : simulationParameters) {

            String name = parameter.getName();
            ParameterType type = parameter.getType();

            if ((type != null) && type.equals(ParameterType.TEXT)){
                switch (name) {
                    case "code_endpoint_url" -> codeEndpointUrl = parameter.getValue();
                    case "token_endpoint_url" -> tokenEndpointUrl = parameter.getValue();
                    case "client_id" -> clientId = parameter.getValue();
                    case "client_secret" -> clientSecret = parameter.getValue();
                    case "scope" -> scope = parameter.getValue();
                    case "person_id" -> personId = parameter.getValue();
                    case "principal" -> principal = parameter.getValue();
                    case "principal_id" -> principalId = parameter.getValue();
                    case "group" -> group = parameter.getValue();
                    case "group_id" -> groupId = parameter.getValue();
                    case "resource" -> resource = parameter.getValue();
                    case "requested_token_type" -> requestedTokenType = parameter.getValue();
                    case "jwt_public_key" -> jwtPublicKey = parameter.getValue();
                }
            }
        }
    }

    /**
     * Validate the configuration.
     * @return AdditionalInstructions information about the validation result.
     */
    public AdditionalInstructions validate() {

        StringBuilder builder = new StringBuilder();
        if (codeEndpointUrl == null || codeEndpointUrl.isBlank())
            builder.append("Authorization code endpoint URL is not set.");

        if (tokenEndpointUrl == null || tokenEndpointUrl.isBlank())
            builder.append("Token endpoint URL is not set.");

        if (clientId == null || clientId.isBlank())
            builder.append("Client id is not set.");

        if (clientSecret == null || clientSecret.isBlank())
            builder.append("Client secret is not set.");

        if (scope == null || scope.isBlank())
            builder.append("Scope is not set.");

        if (jwtPublicKey == null || jwtPublicKey.isBlank())
            builder.append("JWT public key is not set.");

        // eval the scope
        Map<String, String> scopeMap = parseScope(scope);
        String subjectRole = scopeMap.get("subject_role");

        if (subjectRole == null || subjectRole.isBlank()) {
            builder.append("Scope does not contain a subject role.");
            return getAdditionalInstructions(builder.toString());
        }

        // ROLE specific requirements
        if (subjectRole.endsWith(UserRole.ASS)) {
            if (principal == null || principal.isBlank())
                builder.append("Principal is not set but is required for subject role ASS.");

            if (principalId == null || principalId.isBlank())
                builder.append("Principal ID is not set but is required for subject role ASS.");
        }

        String message = builder.toString();
        if (!message.isEmpty()) {
            return getAdditionalInstructions(message);
        }

        return null;
    }

    private @NonNull AdditionalInstructions getAdditionalInstructions(String message) {
        LOG.error(message);
        AdditionalInstructions additionalInstructions = new AdditionalInstructions();
        additionalInstructions.setSimulationId(sequenceId);
        additionalInstructions.setInstruction(message);
        return additionalInstructions;
    }

    public boolean isForExtendedToken() {
        return personId != null && !personId.isEmpty();
    }

    /**
     * Parse the scope string into a map of key-value pairs.
     *
     * @param scopeString the scope as presented in the request
     * @return a map of key-value pairs
     */
    public Map<String, String> parseScope(String scopeString) {

        if (scopeString == null || scopeString.trim().isEmpty()) {
            return Collections.emptyMap();
        }

        // split on whitespace or commas
        String[] tokens = scopeString.trim().split("[\\s,]+");

        Map<String, String> result = new LinkedHashMap<>();
        for (String token : tokens) {

            if (token.isEmpty()) continue;
            int idx = token.indexOf('=');
            if (idx <0 ){
                result.put(token.trim(), "");
            } else {
                String key = token.substring(0, idx).trim();
                String val = token.substring(idx + 1).trim();
                if (!key.isEmpty()) result.put(key, val);
            }
        }
        return result;
    }

}
