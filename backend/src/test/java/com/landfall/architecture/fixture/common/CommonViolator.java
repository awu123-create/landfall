package com.landfall.architecture.fixture.common;

import com.landfall.architecture.fixture.module.beta.internal.BetaSecret;

public final class CommonViolator {

    public String leak() {
        return new BetaSecret().value();
    }
}
