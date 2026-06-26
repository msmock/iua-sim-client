package org.fnm.simulator.simulations.clientCredentials;

import net.ihe.gazelle.simulation.business.callback.TransactionReport;
import org.fnm.simulator.simulations.Status;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * The simulation entry point. Creates the configuration and action objects and runs the simulation.
 */
public class ClientCredentialsSimulation {

    private static final Logger LOG = Logger.getLogger(ClientCredentialsSimulation.class);

    public Status status = Status.READY;
    public final Instant createdAt = Instant.now();

    private final ClientCredentialConfig config;
    private final ClientCredentialTokenRequestAction action;

    public ClientCredentialsSimulation(ClientCredentialConfig config) {
        this.config = config;
        this.action = new ClientCredentialTokenRequestAction(config);
    }

    public TransactionReport run() {
        status = Status.RUNNING;
        TransactionReport transactionReport = action.run();
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

