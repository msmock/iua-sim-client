package org.fnm.simulator.simulations.authorizationCode;

import net.ihe.gazelle.simulation.business.callback.Result;
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
    private final TokenRequestAction tokenRequestAction;

    /**
     * Constructor with configuration parameters.
     * @param config the configuration parameters for the authorization code flow.
     */
    public AuthorizationCodeSimulation(AuthorizationCodeConfig config) {
        this.config = config;
        this.authorizationCodeRequestAction = new AuthorizationCodeRequestAction(config);
        this.tokenRequestAction = new TokenRequestAction(config);
    }

    /**
     * Run the simulation.
     * @return TransactionReport with the result of the test
     */
    public TransactionReport run() {

        status = Status.RUNNING;

        LOG.info("Running authorizationCodeSimulation with session id " + config.sessionId);

        try {

            TransactionReport authorizationCodeRequestReport = authorizationCodeRequestAction.run();
            if (authorizationCodeRequestReport.getResult().equals(Result.FAILED)) {
                return authorizationCodeRequestReport;
            }

            TransactionReport tokenRequestReport = tokenRequestAction.run();
            if (tokenRequestReport.getResult().equals(Result.FAILED)) {
                return tokenRequestReport;
            }

            status = Status.DONE;
            return tokenRequestReport;

        } finally {
            status = Status.DONE;
        }
    }

    /**
     * @return the configuration of the simulation
     */
    public AuthorizationCodeConfig getConfig() {
        return config;
    }
}
