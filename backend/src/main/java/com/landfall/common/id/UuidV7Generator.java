package com.landfall.common.id;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

public final class UuidV7Generator {

    private UuidV7Generator() {}

    public static UUID generate() {
        return UuidCreator.getTimeOrderedEpochPlus1();
    }
}
