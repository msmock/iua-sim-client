package org.fnm.simulator.ressource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import net.ihe.gazelle.simulation.business.sequence.*;
import net.ihe.gazelle.simulation.business.setup.SetupOutcome;
import net.ihe.gazelle.simulation.business.setup.SwitchToExecution;
import net.ihe.gazelle.simulation.jaxrs.api.technical.dto.setup.SimulationRequestDTO;
import net.ihe.gazelle.simulation.jaxrs.api.technical.ws.SimulationAPI;
import org.fnm.simulator.IUAClientSimulationService;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.zip.CRC32;


@Path("")
public class IUAClientSimulatorAPI implements SimulationAPI {

    private static final Logger LOG = Logger.getLogger(IUAClientSimulatorAPI.class);

    @Inject
    IUAClientSimulationService simulationService;

    @Override
    public Response getSimulationSequences() {
        SimulationSequence clientCredentialSequence = simulationService.getClientCredentialSequence();
        Response.ResponseBuilder builder = Response.ok(List.of(clientCredentialSequence));
        builder.header("Content-Type", "application/json");
        return builder.build();
    }

    @Override
    public Response getSimulationSequencesChecksum() {
        CRC32 crc = new CRC32();
        crc.update(simulationService.getClientCredentialSequence().hashCode());
        Response.ResponseBuilder builder = Response.ok(String.valueOf(crc.getValue()));
        builder.header("Content-Type", "application/json");
        return builder.build();
    }

    /**
     * @param callback called callback in the SimulationAPI interface. Shall be a unique identifier for the test session.
     * @param simulationRequest the information required for a single simulation run
     * @return the SetupOutcome
     */
    @Override
    public Response setup(String callback, SimulationRequestDTO simulationRequest) {
        SetupOutcome outcome = simulationService.setup(callback, simulationRequest.getBusinessObject());
        Response.ResponseBuilder builder = Response.ok(outcome);
        builder.header("Content-Type", "application/json");
        return builder.build();
    }

    /**
     *
     * run the simulation with the session id
     *
     * @param simulationSessionId the session id of the simulation
     * @return the SwitchToExecution
     */
    @Override
    public Response resume(String simulationSessionId) {
        simulationService.runSimulation(simulationSessionId, null);
        SwitchToExecution resume = new SwitchToExecution();
        Response.ResponseBuilder builder = Response.ok(resume);
        builder.header("Content-Type", "application/json");
        return builder.build();
    }

}
