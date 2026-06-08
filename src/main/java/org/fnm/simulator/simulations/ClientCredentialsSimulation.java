package org.fnm.simulator.simulations;

import jakarta.inject.Inject;
import net.ihe.gazelle.simulation.business.callback.TransactionReport;
import org.fnm.simulator.IUAClientSimulationService;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;

/**
 * The simulation entry point. Creates the configuration and action objects and runs the simulation.
 */
public class ClientCredentialsSimulation {

    private static final Logger LOG = Logger.getLogger(ClientCredentialsSimulation.class);

    public Status status = Status.READY;
    public final Instant createdAt = Instant.now();

    private final ClientCredentialConfig config;
    private final ClientCredentialAction action;

    public ClientCredentialsSimulation(ClientCredentialConfig config) {
        this.config = config;
        this.action = new ClientCredentialAction(config);
    }

    public TransactionReport run() {
        status = Status.RUNNING;
        TransactionReport transactionReport = action.run();
        transactionReport.setTransaction("CH:IUA Client Credential Flow [ITI-71]");
        transactionReport.setStandards(List.of("CH:IUA"));
        status = Status.DONE;
        return transactionReport;
    }

    public ClientCredentialConfig getConfig() {
        return config;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

}

