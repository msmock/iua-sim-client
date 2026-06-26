package org.fnm.simulator.simulations.authorizationCode;

import net.ihe.gazelle.simulation.business.callback.TransactionReport;
import org.fnm.simulator.simulations.Status;
import org.jboss.logging.Logger;

import java.time.Instant;

public class AuthorizationCodeSimulation {

    private static final Logger LOG = Logger.getLogger(AuthorizationCodeSimulation.class);

    public Status status = Status.READY;
    public final Instant createdAt = Instant.now();

    private final AuthorizationCodeConfig config;
    private final AuthorizationCodeRequestAction authorizationCodeRequestAction;
    private final AuthorizationCodeTokenRequestAction  authorizationCodeTokenRequestAction;

    public AuthorizationCodeSimulation(AuthorizationCodeConfig config) {
        this.config = config;
        this.authorizationCodeRequestAction = new AuthorizationCodeRequestAction(config);
        this.authorizationCodeTokenRequestAction = new AuthorizationCodeTokenRequestAction(config);
    }

    public TransactionReport run() {

        status = Status.RUNNING;

        LOG.info("Running authorizationCodeSimulation with session id " + config.sessionId);

        TransactionReport report = authorizationCodeRequestAction.run();

        status = Status.DONE;
        return report;
    }

    public AuthorizationCodeConfig getConfig() {
        return config;
    }
}
