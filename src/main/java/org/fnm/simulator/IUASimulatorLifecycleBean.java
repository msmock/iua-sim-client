package org.fnm.simulator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.websockets.next.BasicWebSocketConnector;
import io.quarkus.websockets.next.WebSocketClientConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Manage the application lifecycle and schedule service registration via web socket interface to periodically
 * send the service metadata to the test environment.
 *
 * See https://connectathon.ihe-catalyst.net/service-registry/service-registration-api/index.html
 */
@ApplicationScoped
public class IUASimulatorLifecycleBean {

    private static final Logger LOG = Logger.getLogger("IUASimulatorLifecycleBean");

    @ConfigProperty(name = "websocket.url.base")
    String wsUrlBase;

    @ConfigProperty(name = "version")
    String version;

    // TODO How do I know the URL of the simulation service?
    @ConfigProperty(name = "simulationServiceUrl")
    String simulationServiceUrl;

    WebSocketClientConnection connection;

    @Inject
    BasicWebSocketConnector connector;

    final String instanceId = UUID.randomUUID().toString();
    final String replicaId = UUID.randomUUID().toString();

    /**
     *
     * @param ev the start event
     * @throws JsonProcessingException never happens
     */
    void onStart(@Observes StartupEvent ev) throws JsonProcessingException {

        LOG.info("The application is starting...");

        String url = wsUrlBase + "/" + instanceId + "/" + replicaId;

        connection = connector
                .baseUri(url)
                .executionModel(BasicWebSocketConnector.ExecutionModel.NON_BLOCKING)
                .connectAndAwait();
    }

    /**
     * Close the connection to the simulation service
     * @param ev the stop event
     */
    void onStop(@Observes ShutdownEvent ev) {
        LOG.info("The application is stopping...");
        if (connection != null) {
            connection.closeAndAwait();
        }
    }

    /**
     * Register the simulation service periodically
     *
     * @throws JsonProcessingException never happens
     */
    @Scheduled(every = "10s")
    void run() throws JsonProcessingException {
        connection.sendTextAndAwait(getMetadata());
    }

    /**
     * @return the metadata for the simulation service
     * @throws JsonProcessingException never happens
     */
    private String getMetadata() throws JsonProcessingException {

        ObjectMapper mapper = new ObjectMapper();

        ObjectNode registration = mapper.createObjectNode();
        registration.put("name", "IUA Client Simulator");
        registration.put("version", version);
        registration.put("instanceId", instanceId);
        registration.put("replicaId", replicaId);
        registration.put("description", "This service is used to simulate the transaction Get Access Token [ITI-71] of CH:IUA");

        ObjectNode providedInterface = mapper.createObjectNode();
        providedInterface.put("interfaceName", "Simulation Service API");
        providedInterface.put("interfaceVersion", version);

        ObjectNode binding = mapper.createObjectNode();
        binding.put("@type", "REST");
        binding.put("serviceUrl", simulationServiceUrl);
        providedInterface.set("binding", binding);

        ArrayNode providedInterfaces = mapper.createArrayNode();
        providedInterfaces.add(providedInterface);

        registration.set("providedInterfaces", providedInterfaces);

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(registration);
    }

}
