package com.dextercai.dbamcp.domain.asset;

import java.util.Objects;

/** Opaque, stable inventory identifier. It is never a network address or credential. */
public record AssetId(String value) {
    public AssetId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("asset id must contain 1 to 128 characters");
        }
    }
    @Override public String toString() { return value; }
}
