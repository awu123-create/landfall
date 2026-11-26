package com.landfall.architecture.fixture.module.alpha;

import com.landfall.architecture.fixture.module.beta.BetaPublicApi;

public final class AlphaConsumer {

    public String read() {
        return new BetaPublicApi().expose();
    }
}
