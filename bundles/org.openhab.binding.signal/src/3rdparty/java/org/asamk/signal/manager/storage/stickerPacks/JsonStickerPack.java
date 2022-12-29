package org.asamk.signal.manager.storage.stickerPacks;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import org.asamk.signal.manager.api.StickerPack;

public class JsonStickerPack {

    private final String title;
    private final String author;
    private final JsonSticker cover;
    private final List<JsonSticker> stickers;

    public JsonStickerPack(@JsonProperty("title") String title, @JsonProperty("author") String author, @JsonProperty("cover") JsonSticker cover, @JsonProperty("stickers") List<JsonSticker> stickers) {
        super();
        this.title = title;
        this.author = author;
        this.cover = cover;
        this.stickers = stickers;
    }

    public static class JsonSticker {
        private final Integer id;
        private final String emoji;
        private final String file;
        private final String contentType;

        public JsonSticker(@JsonProperty("id") Integer id, @JsonProperty("emoji") String emoji, @JsonProperty("file") String file, @JsonProperty("contentType") String contentType) {
            super();
            this.id = id;
            this.emoji = emoji;
            this.file = file;
            this.contentType = contentType;
        }

        public StickerPack.Sticker toApi() {
            return new StickerPack.Sticker(id == null ? Integer.parseInt(file) : id, emoji, contentType);
        }

        public Integer id() {
            return id;
        }

        public String emoji() {
            return emoji;
        }

        public String file() {
            return file;
        }

        public String contentType() {
            return contentType;
        }
    }

    public String title() {
        return title;
    }

    public String author() {
        return author;
    }

    public JsonSticker cover() {
        return cover;
    }

    public List<JsonSticker> stickers() {
        return stickers;
    }
}
