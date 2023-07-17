package org.asamk.signal.manager.storage.stickers;

import org.asamk.signal.manager.api.StickerPackId;

import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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

    public record Storage(List<Sticker> stickers) {

        public record Sticker(String packId, String packKey, boolean installed) {

        }
    }
}
