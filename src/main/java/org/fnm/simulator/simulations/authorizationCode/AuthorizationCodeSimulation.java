package org.fnm.simulator.simulations.authorizationCode;

import org.fnm.simulator.simulations.Status;
import java.time.Instant;

public class AuthorizationCodeSimulation {

    public Status status = Status.READY;
    public final Instant createdAt = Instant.now();

    private final AuthorizationCodeConfig config;

    public AuthorizationCodeSimulation(AuthorizationCodeConfig config) {
        this.config = config;
    }

}
