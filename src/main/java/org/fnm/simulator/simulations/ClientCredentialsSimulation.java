package org.fnm.simulator.simulations;

import net.ihe.gazelle.simulation.business.callback.TransactionReport;

import java.time.Instant;
import java.util.List;

/**
 * The simulation entry point. Creates the configuration and action objects and runs the simulation.
 */
public class ClientCredentialsSimulation {

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

