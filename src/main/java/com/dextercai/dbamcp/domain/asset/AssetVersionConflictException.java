package com.dextercai.dbamcp.domain.asset;

/** Raised when the client attempts to overwrite an asset with an outdated version. */
public class AssetVersionConflictException extends RuntimeException {
    public AssetVersionConflictException() { super("asset was changed by another request"); }
}
