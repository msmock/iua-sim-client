package org.fnm.simulator.simulations.authorizationCode;

import net.ihe.gazelle.simulation.business.callback.Role;
import net.ihe.gazelle.simulation.business.setup.*;
import org.jboss.logging.Logger;

import java.util.List;

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
    public String scope;
    public String personId;
    public String principal;
    public String principalId;
    public String group;
    public String groupId;

    public String jwtPublicKey;

    /**
     *
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
                    case "jwt_public_key" -> jwtPublicKey = parameter.getValue();
                }
            }
        }
    }

    /**
     * TODO pimp for ROLE specific requirements
     *
     * Validate the configuration.
     */
    public AdditionalInstructions validate() {

        StringBuilder builder = new StringBuilder();
        if (codeEndpointUrl == null || codeEndpointUrl.isEmpty())
            builder.append("Authorization code endpoint URL is not set.");

        if (tokenEndpointUrl == null || tokenEndpointUrl.isEmpty())
            builder.append("Token endpoint URL is not set.");

        if (clientId == null || clientId.isEmpty())
            builder.append("Client id is not set.");

        if (clientSecret == null || clientSecret.isEmpty())
            builder.append("Client secret is not set.");

        if (scope == null || scope.isEmpty())
            builder.append("Scope is not set.");

        if (jwtPublicKey == null || jwtPublicKey.isEmpty())
            builder.append("JWT public key is not set.");



        String message = builder.toString();
        if (!message.isEmpty()) {
            LOG.error(message);
            AdditionalInstructions additionalInstructions = new AdditionalInstructions();
            additionalInstructions.setSimulationId(sequenceId);
            additionalInstructions.setInstruction(message);
            return additionalInstructions; // new SwitchToExecution();
        }

        return null;
    }

    public boolean isForExtendedToken() {
        return personId != null && !personId.isEmpty();
    }

}
