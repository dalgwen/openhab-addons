package org.asamk.signal.manager.storage.stickers;

import org.asamk.signal.manager.api.StickerPackId;

import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public class LegacyStickerStore {

    public static void migrate(Storage storage, StickerStore stickerStore) {
        final var packIds = new HashSet<StickerPackId>();
        final List<StickerPack> stickers = (List<StickerPack>) storage.stickers.stream().map(s -> {
            var packId = StickerPackId.deserialize(Base64.getDecoder().decode(s.packId));
            if (packIds.contains(packId)) {
                // Remove legacy duplicate packIds ...
                return Optional.<StickerPack>empty();
            }
            packIds.add(packId);
            var packKey = Base64.getDecoder().decode(s.packKey);
            var installed = s.installed;
            return Optional.<StickerPack>of(new StickerPack(-1, packId, packKey, installed));
        }).filter(Optional::isPresent).map(Optional::get).toList();

        stickerStore.addLegacyStickers(stickers);
    }

    public record Storage(List<Sticker> stickers) {

        public record Sticker(String packId, String packKey, boolean installed) {

        }
    }
}
