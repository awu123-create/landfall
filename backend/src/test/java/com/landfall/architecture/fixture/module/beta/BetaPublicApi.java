package com.landfall.architecture.fixture.module.beta;

import com.landfall.architecture.fixture.module.beta.internal.BetaSecret;

public final class BetaPublicApi {

    public String expose() {
        return new BetaSecret().value();
    }
}
