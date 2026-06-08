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
import net.ihe.gazelle.simulation.callback.client.technical.SimulationCallbackImpl;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.fnm.simulator.helper.SigningKeyHelper;
import org.fnm.simulator.simulations.ClientCredentialConfig;
import org.fnm.simulator.simulations.ClientCredentialsSimulation;
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

    private static final Logger LOG = Logger.getLogger(IUAClientSimulationService.class);

    @ConfigProperty(name = "callback.url.base")
    String callbackURLBase;

    private final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final SimulationReportValidator validator = new SimulationReportValidator();
    private final Map<String, ClientCredentialsSimulation> simulations = new ConcurrentHashMap<>();

    /**
     * @param sessionId         unique session identifier, called callback in SimulationAPI.
     * @param simulationRequest the information required for a single simulation run
     * @return the SetupOutcome
     */
    @Override
    public SetupOutcome setup(String sessionId, SimulationRequest simulationRequest) {

        // check if simulation is already running
        ClientCredentialsSimulation simulation = simulations.get(sessionId);
        if (simulation != null && simulation.status == Status.RUNNING) {
            String message = "Simulation with session id " + sessionId + " is already running.";
            throw new AlreadyRunningException(message);
        }

        ClientCredentialConfig config = new ClientCredentialConfig(sessionId, simulationRequest);
        AdditionalInstructions validation = config.validate();

        if (validation != null)
            return validation;

        simulation = new ClientCredentialsSimulation(config);
        simulations.put(sessionId, simulation);

        // return "ready to go"
        AdditionalInstructions additionalInstructions = new AdditionalInstructions();
        additionalInstructions.setSimulationId(simulationRequest.getSequenceId());
        additionalInstructions.setInstruction("Test is initialized and can be started!");

        return additionalInstructions; // new SwitchToExecution();
    }

    @Override
    public void runSimulation(String sessionId, SimulationCallback callback) {

        ClientCredentialsSimulation simulation = simulations.get(sessionId);
        if (simulation == null) {
            throw new UnknownSequenceException();
        }

        LOG.info("Running simulation with session id " + sessionId);

        // run the simulation
        TransactionReport transactionReport = simulation.run();

        // build the simulation report
        SimulationReport simulationReport = new SimulationReport();
        simulationReport.setUuid(sessionId);
        simulationReport.setSequenceId(simulation.getConfig().sequenceId);
        simulationReport.setServiceName(this.getClass().getSimpleName());
        simulationReport.setDateTime(Instant.now());
        simulationReport.setResult(transactionReport.getResult());
        simulationReport.setTransactionReports(List.of(transactionReport));
        simulationReport.setServiceVersion("0.5.0");
        simulationReport.setSimulationParameters(simulation.getConfig().simulationParameters);

        LOG.info("Finished simulation with session id " + sessionId);

        notifySimulation(simulationReport);
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
     * TODO: This alternative is preferred from gazelle simulation module but does not work. No class def found for RestClientBuilder
     */
    public void notifySimulationAlt(SimulationReport report) {
        String callbackURL = callbackURLBase + "?session=" + report.getUuid();
        SimulationCallbackImpl impl = new SimulationCallbackImpl(callbackURL);
        impl.notifySimulation(report);
    }

    /**
     * Periodically check for finished and orphaned simulations and removes them from the map.
     */
    @Scheduled(every = "10s")
    void run() {

        // accepted age of simulations before being removed
        long acceptedDelayInSeconds = 10 * 60;

        for (Map.Entry<String, ClientCredentialsSimulation> entry : simulations.entrySet()) {

            String sessionId = entry.getKey();
            ClientCredentialsSimulation simulation = entry.getValue();

            // finished simulations
            if (simulation.status == Status.DONE) {
                LOG.info("Remove simulation with sessionId = " + sessionId + ", timestamp =" + simulation.getCreatedAt() + " and status = " + simulation.status);
                simulations.remove(sessionId);
            }

            // orphaned simulations
            if (simulation.status == Status.READY &&
                    simulation.getCreatedAt().plusSeconds(acceptedDelayInSeconds).isBefore(Instant.now())) {
                LOG.info("Remove orphaned simulation with sessionId = " + sessionId + " and timestamp = " + simulation.getCreatedAt());
                simulations.remove(sessionId, simulation);
            }
        }
    }

    /**
     * @return the SimulationSequence definition for the IUA Client Credential flow
     */
    public SimulationSequence getClientCredentialSequence() {

        SimulationSequence sequence = new SimulationSequence();
        sequence.setId("c74f063b-fb76-405e-8fa3-b2632b5c112f");

        SimulatedRole simulationRole = new SimulatedRole();
        simulationRole.setName("CH:IUA Client");
        simulationRole.setType(RoleType.INITIATOR);
        sequence.setSimulatedRoles(List.of(simulationRole));

        Parameter endpoint = new Parameter();
        endpoint.setName("tokenEndpointUrl").setType(ParameterType.TEXT);
        endpoint.setValue("http://localhost:9000/token");

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

        Parameter person = new Parameter();
        person.setName("person_id").setType(ParameterType.TEXT);
        person.setValue("${patient.spid}");

        String requestScope = "scope=" +
                "purpose_of_use=urn:oid:2.16.756.5.30.1.127.3.10.5|AUTO " +
                "subject_role=urn:oid:2.16.756.5.30.1.127.3.10.6|TC";

        Parameter scope = new Parameter();
        scope.setName("scope").setType(ParameterType.TEXT);
        scope.setValue(requestScope);

        // example key to be overridden by the Authorization Server under test.
        String key = SigningKeyHelper.getExampleRSAPublicKey();

        Parameter publicKey = new Parameter();
        publicKey.setName("jwtPublicKey").setType(ParameterType.TEXT);
        publicKey.setValue(key);

        simulationRole.setConfigs(List.of(endpoint, clientId, clientSecret, principal, principalId, person, scope, publicKey));

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

        sequence.setDescription("""
                        Sequence for the client credential flow of the ITI-71 transaction.
                        In this sequence, the client sends a http POST request to the IUA Server to get an access token.
                        The http request is signed using using http signature as defined in RFC-9421 the private key of the client simulator and shall be verified by the server.
                        """);

        return sequence;
    }


}
