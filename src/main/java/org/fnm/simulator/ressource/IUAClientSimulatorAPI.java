package org.fnm.simulator.ressource;

import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import net.ihe.gazelle.modelmarshaller.technical.jackson.ObjectMapperBuilder;
import net.ihe.gazelle.simulation.business.sequence.*;
import net.ihe.gazelle.simulation.business.setup.AdditionalInstructions;
import net.ihe.gazelle.simulation.business.setup.SetupOutcome;
import net.ihe.gazelle.simulation.business.setup.SwitchToExecution;
import net.ihe.gazelle.simulation.jaxrs.api.technical.dto.sequence.SimulationSequenceDTO;
import net.ihe.gazelle.simulation.jaxrs.api.technical.dto.setup.AdditionalInstructionsDTO;
import net.ihe.gazelle.simulation.jaxrs.api.technical.dto.setup.SetupOutcomeDTO;
import net.ihe.gazelle.simulation.jaxrs.api.technical.dto.setup.SimulationRequestDTO;
import net.ihe.gazelle.simulation.jaxrs.api.technical.dto.setup.SwitchToExecutionDTO;
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
        SimulationSequence authorizationCodeSequence = simulationService.getAuthorizationCodeSequence();

        JsonMapper mapper = new ObjectMapperBuilder().getBuilder().build();
        SimulationSequenceDTO clientCredentialSequenceDTO = new SimulationSequenceDTO(clientCredentialSequence);
        SimulationSequenceDTO authorizationCodeSequenceDTO = new SimulationSequenceDTO(authorizationCodeSequence);

        try {

            Response.ResponseBuilder builder = Response.ok(
                    mapper.writeValueAsString(
                    List.of(clientCredentialSequenceDTO, authorizationCodeSequenceDTO)));
            builder.header("Content-Type", "application/json");
            return builder.build();

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
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
     * Setup the simulation with the given information
     *
     * @param callback called callback in the SimulationAPI interface. Shall be a unique identifier for the test session.
     * @param simulationRequest the information required for a single simulation run
     * @return the SetupOutcome
     */
    @Override
    public Response setup(String callback, SimulationRequestDTO simulationRequest) throws RuntimeException {

        SetupOutcome outcome = simulationService.setup(callback, simulationRequest.getBusinessObject());

        JsonMapper mapper = new ObjectMapperBuilder().getBuilder().build();
        AdditionalInstructionsDTO dto = new AdditionalInstructionsDTO( (AdditionalInstructions) outcome);

        try {

            String result = mapper.writeValueAsString(dto);
            Response.ResponseBuilder builder = Response.ok(result);
            builder.header("Content-Type", "application/json");
            return builder.build();

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }


    }

    /**
     * run the simulation with the session id
     *
     * @param simulationSessionId the session id of the simulation
     * @return the SwitchToExecution
     */
    @Override
    public Response resume(String simulationSessionId) {

        simulationService.runSimulation(simulationSessionId, null);
        SwitchToExecution resume = new SwitchToExecution();

        JsonMapper mapper = new ObjectMapperBuilder().getBuilder().build();
        SwitchToExecutionDTO dto = new SwitchToExecutionDTO(resume);

        try {
            String result = mapper.writeValueAsString(dto);
            Response.ResponseBuilder builder = Response.ok(result);
            builder.header("Content-Type", "application/json");
            return builder.build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
