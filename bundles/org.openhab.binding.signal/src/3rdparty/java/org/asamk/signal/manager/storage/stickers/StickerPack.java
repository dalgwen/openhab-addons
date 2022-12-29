package org.asamk.signal.manager.storage.stickers;


import com.fasterxml.jackson.annotation.JsonProperty;

import org.asamk.signal.manager.api.StickerPackId;

public class StickerPack {

    private final long internalId;
    private final StickerPackId packId;
    private final byte[] packKey;
    private final boolean isInstalled;

    public StickerPack(@JsonProperty("internalId") long internalId, @JsonProperty("packId") StickerPackId packId, @JsonProperty("packKey") byte[] packKey, @JsonProperty("isInstalled") boolean isInstalled) {
        super();
        this.internalId = internalId;
        this.packId = packId;
        this.packKey = packKey;
        this.isInstalled = isInstalled;
    }

    public StickerPack(@JsonProperty("packId") final StickerPackId packId, @JsonProperty("packKey") final byte[] packKey) {
        this(-1, packId, packKey, false);
    }

    public long internalId() {
        return internalId;
    }

    public StickerPackId packId() {
        return packId;
    }

    public byte[] packKey() {
        return packKey;
    }

    public boolean isInstalled() {
        return isInstalled;
    }
}
