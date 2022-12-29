package org.asamk.signal.manager.api;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

public class StickerPack {
    private final StickerPackId packId;
    private final StickerPackUrl url;
    private final boolean installed;
    private final String title;
    private final String author;
    private final Optional<Sticker> cover;
    private final List<Sticker> stickers;

    public StickerPack(@JsonProperty("packId") StickerPackId packId, @JsonProperty("url") StickerPackUrl url, @JsonProperty("installed") boolean installed, @JsonProperty("title") String title, @JsonProperty("author") String author,
            @JsonProperty("cover") Optional<Sticker> cover, @JsonProperty("stickers") List<Sticker> stickers) {
        super();
        this.packId = packId;
        this.url = url;
        this.installed = installed;
        this.title = title;
        this.author = author;
        this.cover = cover;
        this.stickers = stickers;
    }

    public StickerPack(@JsonProperty("packId") final StickerPackId packId, @JsonProperty("packKey") final byte[] packKey, @JsonProperty("installed") final boolean installed) {
        this(packId, new StickerPackUrl(packId, packKey), installed, "", "", Optional.empty(), List.of());
    }

    public static class Sticker {
        private final int id;
        private final String emoji;
        private final String contentType;

        public Sticker(@JsonProperty("id") int id, @JsonProperty("emoji") String emoji, @JsonProperty("contentType") String contentType) {
            super();
            this.id = id;
            this.emoji = emoji;
            this.contentType = contentType;
        }

        public int id() {
            return id;
        }

        public String emoji() {
            return emoji;
        }

        public String contentType() {
            return contentType;
        }

    }

    public StickerPackId packId() {
        return packId;
    }

    public StickerPackUrl url() {
        return url;
    }

    public boolean installed() {
        return installed;
    }

    public String title() {
        return title;
    }

    public String author() {
        return author;
    }

    public Optional<Sticker> cover() {
        return cover;
    }

    public List<Sticker> stickers() {
        return stickers;
    }
}
