package org.fnm.simulator;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import net.ihe.gazelle.modelmarshaller.technical.jackson.ObjectMapperBuilder;
import net.ihe.gazelle.simulation.business.callback.*;
import net.ihe.gazelle.simulation.business.sequence.*;
import net.ihe.gazelle.simulation.business.setup.*;
import net.ihe.gazelle.simulation.jaxrs.api.technical.dto.callback.SimulationReportDTO;
import net.ihe.gazelle.simulation.jaxrs.api.technical.dto.setup.AdditionalInstructionsDTO;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.fnm.simulator.helper.SigningKeyHelper;
import org.fnm.simulator.simulations.authorizationCode.AuthorizationCodeConfig;
import org.fnm.simulator.simulations.authorizationCode.AuthorizationCodeSimulation;
import org.fnm.simulator.simulations.clientCredentials.ClientCredentialConfig;
import org.fnm.simulator.simulations.clientCredentials.ClientCredentialsSimulation;
import org.fnm.simulator.simulations.Status;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The core simulation service. It provides the service API and manages the individual simulation runs.
 */
@ApplicationScoped
public class IUAClientSimulationService implements SimulationService {

    private static final Logger LOG = Logger.getLogger(IUAClientSimulationService.class);

    public static final String CLIENT_CREDENTIAL_SEQUENCE_ID = "c74f063b-fb76-405e-8fa3-b2632b5c112f";
    public static final String AUTHORIZATION_CODE_SEQUENCE_ID = "0064e130-cf31-40f5-ad62-163af639b360";

    @ConfigProperty(name = "callback.url.base")
    String callbackURLBase;

    @ConfigProperty(name = "version")
    String version;

    @ConfigProperty(name = "access-token")
    String accessToken;

    private final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final SimulationReportValidator validator = new SimulationReportValidator();

    private final Map<String, ClientCredentialsSimulation> clientCredentialSimulations = new ConcurrentHashMap<>();
    private final Map<String, AuthorizationCodeSimulation> authorizationCodeSimulations = new ConcurrentHashMap<>();

    private static final String SPID = "761337610411353650^^^&2.16.756.5.30.1.127.3.10.3&ISO";

    /**
     *
     * @param sessionId         unique session identifier, called callback in SimulationAPI.
     * @param simulationRequest the information required for a single simulation run
     * @return the SetupOutcome
     */
    @Override
    public SetupOutcome setup(String sessionId, SimulationRequest simulationRequest) throws RuntimeException {

        // check if simulation is already running
        ClientCredentialsSimulation clientCredentialsSimulation = clientCredentialSimulations.get(sessionId);
        if (clientCredentialsSimulation != null && clientCredentialsSimulation.status == Status.RUNNING) {
            String message = "Simulation with session id " + sessionId + " is already running.";
            throw new AlreadyRunningException(message);
        }

        AuthorizationCodeSimulation authorizationCodeSimulation = authorizationCodeSimulations.get(sessionId);
        if (authorizationCodeSimulation != null && authorizationCodeSimulation.status == Status.RUNNING) {
            String message = "Simulation with session id " + sessionId + " is already running.";
            throw new AlreadyRunningException(message);
        }

        String sequenceId = simulationRequest.getSequenceId();

        if (sequenceId.equals(CLIENT_CREDENTIAL_SEQUENCE_ID)) {

            ClientCredentialConfig config = new ClientCredentialConfig(sessionId, simulationRequest);
            AdditionalInstructions validation = config.validate();

            if (validation != null) return validation;

            clientCredentialsSimulation = new ClientCredentialsSimulation(config);
            clientCredentialSimulations.put(sessionId, clientCredentialsSimulation);

            // return "ready to go"
            AdditionalInstructions additionalInstructions = new AdditionalInstructions();
            additionalInstructions.setSimulationId(sequenceId);

            StringBuilder message = new StringBuilder();
            message.append("Test for the client credentials sequence is initialized and can be started! ");

            if (config.isForExtendedToken())
                message.append("Since the person_id is set, an extended access token will be requested.");
            else
                message.append("Since the person_id is not set, a basic access token will be requested.");

            additionalInstructions.setInstruction(message.toString());
            return additionalInstructions; // new SwitchToExecution();
        }

        if (sequenceId.equals(AUTHORIZATION_CODE_SEQUENCE_ID)) {

            AuthorizationCodeConfig config = new AuthorizationCodeConfig(sessionId, simulationRequest);
            AdditionalInstructions validation = config.validate();

            if (validation != null) return validation;

            authorizationCodeSimulation = new AuthorizationCodeSimulation(config);
            authorizationCodeSimulations.put(sessionId, authorizationCodeSimulation);

            // return "ready to go"
            AdditionalInstructions additionalInstructions = new AdditionalInstructions();
            additionalInstructions.setSimulationId(sequenceId);

            StringBuilder message = new StringBuilder();
            message.append("Test for the authorization code sequence is initialized and can be started! ");

            if (config.isForExtendedToken())
                message.append("Since the person_id is set, an extended access token will be requested.");
            else
                message.append("Since the person_id is not set, a basic access token will be requested.");

            additionalInstructions.setInstruction(message.toString());
            return additionalInstructions; // new SwitchToExecution();
        }

        throw new UnknownSequenceException();

    }

    /**
     * @param sessionId the current test session id
     * @param callback  callback to be notified when the simulation is finished
     */
    @Override
    public void runSimulation(String sessionId, SimulationCallback callback) {

        // get either the cc or the ac clientCredentialsSimulation from sessionId
        ClientCredentialsSimulation clientCredentialsSimulation = clientCredentialSimulations.get(sessionId);
        if (clientCredentialsSimulation != null) {

            LOG.info("Running clientCredentialsSimulation with session id " + sessionId);

            // run the clientCredentialsSimulation
            TransactionReport transactionReport = clientCredentialsSimulation.run();

            // build the clientCredentialsSimulation report
            SimulationReport simulationReport = new SimulationReport();
            simulationReport.setUuid(sessionId);
            simulationReport.setSequenceId(clientCredentialsSimulation.getConfig().sequenceId);
            simulationReport.setServiceName(this.getClass().getSimpleName());
            simulationReport.setDateTime(Instant.now());
            simulationReport.setResult(transactionReport.getResult());
            simulationReport.setTransactionReports(List.of(transactionReport));
            simulationReport.setServiceVersion("0.5.0");
            simulationReport.setSimulationParameters(clientCredentialsSimulation.getConfig().simulationParameters);

            LOG.info("Finished clientCredentialsSimulation with session id " + sessionId);

            notifySimulation(simulationReport);
            return;
        }

        AuthorizationCodeSimulation authorizationCodeSimulation = authorizationCodeSimulations.get(sessionId);
        if (authorizationCodeSimulation != null) {

            LOG.info("Running authorizationCodeSimulation with session id " + sessionId);

            // run the clientCredentialsSimulation
            TransactionReport transactionReport = authorizationCodeSimulation.run();

            // build the clientCredentialsSimulation report
            SimulationReport simulationReport = new SimulationReport();
            simulationReport.setUuid(sessionId);
            simulationReport.setSequenceId(authorizationCodeSimulation.getConfig().sequenceId);
            simulationReport.setServiceName(this.getClass().getSimpleName());
            simulationReport.setDateTime(Instant.now());
            simulationReport.setResult(transactionReport.getResult());
            simulationReport.setTransactionReports(List.of(transactionReport));
            simulationReport.setServiceVersion(version);
            simulationReport.setSimulationParameters(authorizationCodeSimulation.getConfig().simulationParameters);

            LOG.info("Finished authorizationCodeSimulation with session id " + sessionId);

            notifySimulation(simulationReport);
            return;
        }

        // if neither clientCredentialsSimulation nor authorizationCodeSimulation is setup
        throw new UnknownSequenceException();

    }

    /**
     * The client shall present the access token in the http Authorization header using the Bearer token scheme:
     * Authorization: Bearer <access_token>
     *
     * @param report the simulation report to be send to the test platform
     */
    public void notifySimulation(SimulationReport report) {

        this.validator.validate(report).orThrow(MalformedSimulationReportException::new);

        try {

            JsonMapper mapper = new ObjectMapperBuilder().getBuilder().build();
            SimulationReportDTO dto = new SimulationReportDTO(report);
            String result = mapper.writeValueAsString(dto);

            Log.info("Sending report: " + result);

            // put the access token in the Authentication header
            String authHeader = buildAuthHeader(accessToken);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(callbackURLBase + "?session=" + report.getUuid()))
                    .header("Content-Type", "application/json")
                    .header("Cache-Control", "no-cache")
                    .header("Authorization", authHeader)
                    .POST(HttpRequest.BodyPublishers.ofString(result))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Callback failed with HTTP " + response.statusCode() + ": " + response.body()
                );
            }

        } catch (ConnectException e) {
            LOG.error("Failed to connect to callback endpoint", e);
        } catch (IOException e) {
            LOG.error("Failed to notify callback endpoint", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Callback request was interrupted", e);
        }
    }

    /**
     * Periodically check for finished and orphaned simulations and removes them from the map.
     */
    @Scheduled(every = "10s")
    void run() {

        // accepted age of simulations before being removed
        long acceptedDelayInSeconds = 10 * 60;

        for (Map.Entry<String, ClientCredentialsSimulation> entry : clientCredentialSimulations.entrySet()) {

            String sessionId = entry.getKey();
            ClientCredentialsSimulation simulation = entry.getValue();

            // finished simulations
            if (simulation.status == Status.DONE) {
                LOG.info("Remove simulation with sessionId = " + sessionId + ", timestamp =" + simulation.getCreatedAt() + " and status = " + simulation.status);
                clientCredentialSimulations.remove(sessionId);
            }

            // orphaned simulations
            if (simulation.status == Status.READY &&
                    simulation.getCreatedAt().plusSeconds(acceptedDelayInSeconds).isBefore(Instant.now())) {
                LOG.info("Remove orphaned simulation with sessionId = " + sessionId + " and timestamp = " + simulation.getCreatedAt());
                clientCredentialSimulations.remove(sessionId, simulation);
            }
        }
    }

    /**
     * @return the SimulationSequence definition for the IUA Client Credential flow
     */
    public SimulationSequence getClientCredentialSequence() {

        SimulationSequence sequence = new SimulationSequence();
        sequence.setId(CLIENT_CREDENTIAL_SEQUENCE_ID);

        SupportedParameter tokenEndpoint = new SupportedParameter();
        tokenEndpoint.setName("token_endpoint_url").setType(ParameterType.TEXT);
        tokenEndpoint.setDefaultValue("http://localhost:9000/token").setRequired(true);
        tokenEndpoint.setDescription("The URL of the token endpoint of the system under test.");

        SupportedParameter clientId = new SupportedParameter();
        clientId.setName("client_id").setType(ParameterType.TEXT);
        clientId.setDefaultValue("client-id").setRequired(true);
        clientId.setDescription("The client id of the IUA client simulator.");

        SupportedParameter clientSecret = new SupportedParameter();
        clientSecret.setName("client_secret").setType(ParameterType.TEXT);
        clientSecret.setDefaultValue("client-secret").setRequired(true);
        clientSecret.setDescription("The client secret of the IUA client simulator.");

        SupportedParameter principal = new SupportedParameter();
        principal.setName("principal").setType(ParameterType.TEXT);
        principal.setDefaultValue("principal.name").setRequired(true);
        principal.setDescription("The name of the responsible person for the request.");

        SupportedParameter principalId = new SupportedParameter();
        principalId.setName("principal_id").setType(ParameterType.TEXT);
        principalId.setDefaultValue("principal.id").setRequired(true);
        principalId.setDescription("The GLN of the responsible person for the request.");

        String requestScope = "purpose_of_use=urn:oid:2.16.756.5.30.1.127.3.10.5|AUTO " +
                "subject_role=urn:oid:2.16.756.5.30.1.127.3.10.6|TC";

        SupportedParameter scope = new SupportedParameter();
        scope.setName("scope").setType(ParameterType.TEXT);
        scope.setDefaultValue(requestScope).setRequired(true);
        scope.setDescription("The scope to be requested defining the user role and purpose of use.");

        SupportedParameter personId = new SupportedParameter();
        personId.setName("person_id").setType(ParameterType.TEXT);
        personId.setDefaultValue(SPID).setRequired(false);
        personId.setDescription("The SPID of the patient dossier to be accessed, required for extended access token.");

        // example key to be overridden by the Authorization Server under test.
        String key = SigningKeyHelper.getRSAPublicKey();

        SupportedParameter publicKey = new SupportedParameter();
        publicKey.setName("jwt_public_key").setType(ParameterType.TEXT);
        publicKey.setDefaultValue(key).setRequired(true);
        publicKey.setDescription("The public key to verify the JWT signature of the system under test.");

        // add parameter to the sequence
        sequence.setSupportedParameters(List.of(
                tokenEndpoint,
                clientId,
                clientSecret,
                scope,
                personId,
                principal,
                principalId,
                publicKey
        ));

        sequence.setTransactions(List.of("Get Access Token [ITI-71]"));
        sequence.setShortDescription("Sequence for the ITI-71 client credential flow.");
        sequence.setDescription(
                "Sequence for the client credential flow of the ITI-71 transaction." +
                        "In this sequence, the client sends a http POST request to the IUA Server to get an access token." +
                        "The http request is signed using using http signature as defined in RFC-9421 the private key of the client simulator."
        );

        sequence.setStandards(List.of(
                "CH:IUA Get Access Token [ITI-71]",
                "RFC-9421 HTTP Message Signatures",
                "OAuth 2.1 draft-ietf-oauth-v2-1-15",
                "RFC 7519 JSON Web Token (JWT)",
                "RFC 7515 JSON Web Signature (JWS)",
                "and Standards referenced therein"
        ));

        // add the tested role
        TestedRole testedRole = new TestedRole();
        testedRole.setName("CH:IUA Server");
        testedRole.setType(RoleType.RESPONDER);
        sequence.setTestedRoles(List.of(testedRole));

        // add the simulationRole
        SimulatedRole simulationRole = new SimulatedRole();
        simulationRole.setName("CH:IUA Client");
        simulationRole.setType(RoleType.INITIATOR);
        sequence.setSimulatedRoles(List.of(simulationRole));

        // add the public keys as read-only parameter to simulationRole
        try{

            Parameter httpSignaturePublicKey = new Parameter();
            httpSignaturePublicKey.setName("http_signature_public_key").setType(ParameterType.TEXT);
            httpSignaturePublicKey.setValue(SigningKeyHelper.getEcPublicKeyJWK().toJSONString());

            Parameter idPSignaturePublicKey = new Parameter();
            idPSignaturePublicKey.setName("idp_signature_public_key").setType(ParameterType.TEXT);
            idPSignaturePublicKey.setValue(SigningKeyHelper.getRSAPublicKeyJWK().toJSONString());

            simulationRole.setConfigs(List.of(httpSignaturePublicKey, idPSignaturePublicKey));

        } catch (ParseException e) {
            LOG.error("Failed to create public keys.", e);
            throw new RuntimeException(e);
        }

        LOG.info("Return the Client Credential Flow Sequence: " + sequence);
        return sequence;
    }


    /**
     * @return the SimulationSequence definition for the IUA Client Credential flow
     */
    public SimulationSequence getAuthorizationCodeSequence() {

        SimulationSequence sequence = new SimulationSequence();
        sequence.setId(AUTHORIZATION_CODE_SEQUENCE_ID);

        SupportedParameter codeEndpoint = new SupportedParameter();
        codeEndpoint.setName("code_endpoint_url").setType(ParameterType.TEXT);
        codeEndpoint.setDefaultValue("http://localhost:9000/authorize").setRequired(true);
        codeEndpoint.setDescription("The URL of the code endpoint of the system under test.");

        SupportedParameter tokenEndpoint = new SupportedParameter();
        tokenEndpoint.setName("token_endpoint_url").setType(ParameterType.TEXT);
        tokenEndpoint.setDefaultValue("http://localhost:9000/token").setRequired(true);
        tokenEndpoint.setDescription("The URL of the token endpoint of the system under test.");

        SupportedParameter clientId = new SupportedParameter();
        clientId.setName("client_id").setType(ParameterType.TEXT);
        clientId.setDefaultValue("client-id").setRequired(true);
        clientId.setDescription("The client id of the IUA client simulator.");

        SupportedParameter clientSecret = new SupportedParameter();
        clientSecret.setName("client_secret").setType(ParameterType.TEXT);
        clientSecret.setDefaultValue("client-secret").setRequired(true);
        clientSecret.setDescription("The client secret of the IUA client simulator.");

        // the scope to be overridden in th test setup.
        String requestScope = "purpose_of_use=urn:oid:2.16.756.5.30.1.127.3.10.5|NORMAL " +
                "subject_role=urn:oid:2.16.756.5.30.1.127.3.10.6|HCP";

        SupportedParameter scope = new SupportedParameter();
        scope.setName("scope").setType(ParameterType.TEXT);
        scope.setDefaultValue(requestScope).setRequired(true);
        scope.setDescription("The scope to be requested defining the user role and purpose of use.");

        // the patient epr to be accessed, required for extended access token
        SupportedParameter personId = new SupportedParameter();
        personId.setName("person_id").setType(ParameterType.TEXT);
        personId.setDefaultValue(SPID).setRequired(false);
        personId.setDescription("The SPID of the patient dossier to be accessed, required for extended access token.");

        // principal claim, only for role ASS
        SupportedParameter principal = new SupportedParameter();
        principal.setName("principal").setType(ParameterType.TEXT);
        principal.setDefaultValue("principal.name").setRequired(false);
        principal.setDescription("The name of the responsible person for the request. Required for assistants with role ASS.");

        SupportedParameter principalId = new SupportedParameter();
        principalId.setName("principal_id").setType(ParameterType.TEXT);
        principalId.setDefaultValue("principal.id").setRequired(false);
        principalId.setDescription("The GLN of the responsible person for the request. Required for assistants with role ASS.");

        // optional group claim, only for role HCP and ASS
        SupportedParameter group = new SupportedParameter();
        group.setName("group").setType(ParameterType.TEXT);
        group.setDefaultValue("group.name").setRequired(false);
        group.setDescription("The name of the institution or group the request ist performed on behalf. Optional for role HCP and ASS.");

        SupportedParameter groupId = new SupportedParameter();
        groupId.setName("group").setType(ParameterType.TEXT);
        groupId.setDefaultValue("group.id").setRequired(false);
        groupId.setDescription("The OID of the institution or group the request ist performed on behalf. Optional for role HCP and ASS.");

        // example key to be overridden by the Authorization Server under test.
        String key = SigningKeyHelper.getRSAPublicKey();

        SupportedParameter serverPublicKey = new SupportedParameter();
        serverPublicKey.setName("jwt_public_key").setType(ParameterType.TEXT);
        serverPublicKey.setDefaultValue(key).setRequired(true);
        serverPublicKey.setDescription("The public key to verify the JWT signature of the system under test.");

        // add supported parameter to be set by the SUT
        sequence.setSupportedParameters(List.of(
                codeEndpoint,
                tokenEndpoint,
                clientId,
                clientSecret,
                scope,
                personId,
                principal,
                principalId,
                group,
                groupId,
                serverPublicKey));

        SimulatedRole simulationRole = new SimulatedRole();
        simulationRole.setName("CH:IUA Client");
        simulationRole.setType(RoleType.INITIATOR);
        sequence.setSimulatedRoles(List.of(simulationRole));

        // add the public keys as read only parameter to sumulationRole

        TestedRole testedRole = new TestedRole();
        testedRole.setName("CH:IUA Server");
        testedRole.setType(RoleType.RESPONDER);
        sequence.setTestedRoles(List.of(testedRole));

        sequence.setStandards(List.of(
                "CH:IUA Get Access Token [ITI-71]",
                "RFC-9421 HTTP Message Signatures",
                "OAuth 2.1 draft-ietf-oauth-v2-1-15",
                "RFC 7519 JSON Web Token (JWT)",
                "RFC 7515 JSON Web Signature (JWS)",
                "and Standards referenced therein"
        ));

        sequence.setTransactions(List.of("Get Access Token [ITI-71]"));

        sequence.setShortDescription("Sequence for the ITI-71 authorization code flow.");

        sequence.setDescription(
                "Sequence for the authorization code flow of the ITI-71 transaction." +
                        "In this sequence, the client first sends a http Get request to the IUA Server to get an authorization code." +
                        "In the second step the client sends a http POST request to the IUA Server to exchange the authorization code to an access token." +
                        "The second http request is signed using http signature as defined in RFC-9421 with the private key of the simulator."
        );

        LOG.info("Return the Authorization Code Flow Sequence: " + sequence);
        return sequence;
    }

    /**
     * Build the Authorization header for the client credential flow.
     *
     * @return encoded authorization header with content clientId:clientSecret
     */
    private String buildAuthHeader(String accessToken) {
        String encodedCredentials = Base64.getEncoder().encodeToString(accessToken.getBytes());
        return "Basic " + encodedCredentials;
    }
}
