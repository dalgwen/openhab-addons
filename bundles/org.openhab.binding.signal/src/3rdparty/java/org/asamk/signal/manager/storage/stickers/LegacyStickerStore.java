package org.asamk.signal.manager.storage.stickers;

import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.asamk.signal.manager.api.StickerPackId;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LegacyStickerStore {

    public static void migrate(Storage storage, StickerStore stickerStore) {
        final var packIds = new HashSet<StickerPackId>();
        @SuppressWarnings("null")
        final var stickers = storage.stickers.stream().map(s -> {
            @SuppressWarnings("null")
            var packId = StickerPackId.deserialize(Base64.getDecoder().decode(s.packId));
            if (packIds.contains(packId)) {
                // Remove legacy duplicate packIds ...
                return Optional.<StickerPack>empty();
            }
            packIds.add(packId);
            var packKey = Base64.getDecoder().decode(s.packKey);
            var installed = s.installed;
            return Optional.of(new StickerPack(-1, packId, packKey, installed));
        }).map(Optional::get).filter(Objects::nonNull).collect(Collectors.toList());

        stickerStore.addLegacyStickers(stickers);
    }

    public static class Storage {

        private final List<Sticker> stickers;

        public Storage(@JsonProperty("stickers") List<Sticker> stickers) {
            super();
            this.stickers = stickers;
        }

        private static class Sticker {
            private final String packId;
            private final String packKey;
            private final boolean installed;

            @SuppressWarnings("unused")
            public Sticker(@JsonProperty("packId") String packId, @JsonProperty("packKey") String packKey,
                    @JsonProperty("installed") boolean installed) {
                super();
                this.packId = packId;
                this.packKey = packKey;
                this.installed = installed;
            }

            @SuppressWarnings("unused")
            public String packId() {
                return packId;
            }

            @SuppressWarnings("unused")
            public String packKey() {
                return packKey;
            }

            @SuppressWarnings("unused")
            public boolean installed() {
                return installed;
            }

        }

        public List<Sticker> stickers() {
            return stickers;
        }
    }
}
