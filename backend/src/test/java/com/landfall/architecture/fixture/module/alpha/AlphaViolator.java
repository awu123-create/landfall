package com.landfall.architecture.fixture.module.alpha;

import com.landfall.architecture.fixture.module.beta.internal.BetaSecret;

public final class AlphaViolator {

    public String leak() {
        return new BetaSecret().value();
    }
}
