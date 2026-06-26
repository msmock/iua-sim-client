package org.fnm.simulator;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.vertx.runtime.jackson.InstantSerializer;
import jakarta.enterprise.context.ApplicationScoped;
import net.ihe.gazelle.simulation.business.callback.*;
import net.ihe.gazelle.simulation.business.sequence.*;
import net.ihe.gazelle.simulation.business.setup.*;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The core simulation service. It provides the service API and manages the individual simulation runs.
 */
@ApplicationScoped
public class IUAClientSimulationService implements SimulationService {

    public static final String CLIENT_CREDENTIAL_SEQUENCE_ID = "c74f063b-fb76-405e-8fa3-b2632b5c112f";
    public static final String AUTHORIZATION_CODE_SEQUENCE_ID = "0064e130-cf31-40f5-ad62-163af639b360";

    private static final Logger LOG = Logger.getLogger(IUAClientSimulationService.class);

    @ConfigProperty(name = "callback.url.base")
    String callbackURLBase;

    private final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final SimulationReportValidator validator = new SimulationReportValidator();

    private final Map<String, ClientCredentialsSimulation> clientCredentialSimulations = new ConcurrentHashMap<>();
    private final Map<String, AuthorizationCodeSimulation> authorizationCodeSimulations = new ConcurrentHashMap<>();

    /**
     *
     * @param sessionId         unique session identifier, called callback in SimulationAPI.
     * @param simulationRequest the information required for a single simulation run
     * @return the SetupOutcome
     */
    @Override
    public SetupOutcome setup(String sessionId, SimulationRequest simulationRequest) {

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
            simulationReport.setServiceVersion("0.5.0");
            simulationReport.setSimulationParameters(authorizationCodeSimulation.getConfig().simulationParameters);

            LOG.info("Finished authorizationCodeSimulation with session id " + sessionId);

            notifySimulation(simulationReport);
            return;
        }

        // if neither clientCredentialsSimulation nor authorizationCodeSimulation is setup
        throw new UnknownSequenceException();

    }

    /**
     * @param report the simulation report to be send to the test platform
     */
    public void notifySimulation(SimulationReport report) {

        this.validator.validate(report).orThrow(MalformedSimulationReportException::new);

        try {

            JsonMapper mapper = new JsonMapper();

            // not nice, but required to serialize the simulation report
            SimpleModule timeModule = new SimpleModule();
            timeModule.addSerializer(Instant.class, new InstantSerializer());
            mapper.registerModule(timeModule);
            mapper.disable(MapperFeature.REQUIRE_HANDLERS_FOR_JAVA8_TIMES); // TODO improve and register handler
            mapper.disable(MapperFeature.REQUIRE_HANDLERS_FOR_JAVA8_OPTIONALS);

            String result = mapper.writeValueAsString(report);

            Log.info("Sending report: " + result);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(callbackURLBase + "?session=" + report.getUuid()))
                    .header("Content-Type", "application/json")
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

        SimulatedRole simulationRole = new SimulatedRole();
        simulationRole.setName("CH:IUA Client");
        simulationRole.setType(RoleType.INITIATOR);
        sequence.setSimulatedRoles(List.of(simulationRole));

        Parameter tokenEndpoint = new Parameter();
        tokenEndpoint.setName("token_endpoint_url").setType(ParameterType.TEXT);
        tokenEndpoint.setValue("http://localhost:9000/token");

        Parameter clientId = new Parameter();
        clientId.setName("client_id").setType(ParameterType.TEXT);
        clientId.setValue("${client-id}}");

        Parameter clientSecret = new Parameter();
        clientSecret.setName("client_secret").setType(ParameterType.TEXT);
        clientSecret.setValue("${client-secret}}");

        Parameter principal = new Parameter();
        principal.setName("principal").setType(ParameterType.TEXT);
        principal.setValue("${principal.name}");

        Parameter principalId = new Parameter();
        principalId.setName("principal_id").setType(ParameterType.TEXT);
        principalId.setValue("${principal.gln}");

        Parameter personId = new Parameter();
        personId.setName("person_id").setType(ParameterType.TEXT);
        personId.setValue("${patient.spid}");

        String requestScope = "purpose_of_use=urn:oid:2.16.756.5.30.1.127.3.10.5|AUTO " +
                "subject_role=urn:oid:2.16.756.5.30.1.127.3.10.6|TC";

        Parameter scope = new Parameter();
        scope.setName("scope").setType(ParameterType.TEXT);
        scope.setValue(requestScope);

        // example key to be overridden by the Authorization Server under test.
        String key = SigningKeyHelper.getExampleRSAPublicKey();

        Parameter publicKey = new Parameter();
        publicKey.setName("jwt_public_key").setType(ParameterType.TEXT);
        publicKey.setValue(key);

        simulationRole.setConfigs(List.of(
                tokenEndpoint,
                clientId,
                clientSecret,
                scope,
                personId,
                principal,
                principalId,
                publicKey));

        TestedRole testedRole = new TestedRole();
        testedRole.setName("CH:IUA Server");
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

        sequence.setShortDescription("Sequence for the ITI-71 client credential flow.");

        sequence.setDescription(
                "Sequence for the client credential flow of the ITI-71 transaction." +
                        "In this sequence, the client sends a http POST request to the IUA Server to get an access token." +
                        "The http request is signed using using http signature as defined in RFC-9421 the private key of the client simulator."
        );

        return sequence;
    }


    /**
     * @return the SimulationSequence definition for the IUA Client Credential flow
     */
    public SimulationSequence getAuthorizationCodeSequence() {

        SimulationSequence sequence = new SimulationSequence();
        sequence.setId(AUTHORIZATION_CODE_SEQUENCE_ID);

        SimulatedRole simulationRole = new SimulatedRole();
        simulationRole.setName("CH:IUA Client");
        simulationRole.setType(RoleType.INITIATOR);
        sequence.setSimulatedRoles(List.of(simulationRole));

        Parameter codeEndpoint = new Parameter();
        codeEndpoint.setName("code_endpoint_url").setType(ParameterType.TEXT);
        codeEndpoint.setValue("http://localhost:9000/authorize");

        Parameter tokenEndpoint = new Parameter();
        tokenEndpoint.setName("token_endpoint_url").setType(ParameterType.TEXT);
        tokenEndpoint.setValue("http://localhost:9000/token");

        Parameter clientId = new Parameter();
        clientId.setName("client_id").setType(ParameterType.TEXT);
        clientId.setValue("${client-id}}");

        Parameter clientSecret = new Parameter();
        clientSecret.setName("client_secret").setType(ParameterType.TEXT);
        clientSecret.setValue("${client-secret}}");

        // the scope to be overridden in th test setup.
        String requestScope = "purpose_of_use=urn:oid:2.16.756.5.30.1.127.3.10.5|NORMAL " +
                "subject_role=urn:oid:2.16.756.5.30.1.127.3.10.6|HCP";

        Parameter scope = new Parameter();
        scope.setName("scope").setType(ParameterType.TEXT);
        scope.setValue(requestScope);

        // the patient epr to be accessed, required for extended access token
        Parameter personId = new Parameter();
        personId.setName("person_id").setType(ParameterType.TEXT);
        personId.setValue("${patient.spid}");

        // principal claim, only for role ASS
        Parameter principal = new Parameter();
        principal.setName("principal").setType(ParameterType.TEXT);
        principal.setValue("${principal.name}");

        Parameter principalId = new Parameter();
        principalId.setName("principal_id").setType(ParameterType.TEXT);
        principalId.setValue("${principal.gln}");

        // optional group claim, only for role HCP and ASS
        Parameter group = new Parameter();
        group.setName("group").setType(ParameterType.TEXT);
        group.setValue("${group.name}");

        Parameter groupId = new Parameter();
        groupId.setName("group_id").setType(ParameterType.TEXT);
        groupId.setValue("${group.name}");

        // example key to be overridden by the Authorization Server under test.
        String key = SigningKeyHelper.getExampleRSAPublicKey();

        Parameter serverPublicKey = new Parameter();
        serverPublicKey.setName("jwt_public_key").setType(ParameterType.TEXT);
        serverPublicKey.setValue(key);

        simulationRole.setConfigs(List.of(
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

        TestedRole testedRole = new TestedRole();
        testedRole.setName("CH:IUA Server");
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

        return sequence;

    }
}
