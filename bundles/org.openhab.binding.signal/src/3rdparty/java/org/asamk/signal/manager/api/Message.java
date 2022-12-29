package org.asamk.signal.manager.api;

import java.util.List;
import java.util.Optional;

import org.asamk.signal.manager.api.RecipientIdentifier.Single;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Message {

    private final String messageText;
    private final List<String> attachments;
    private final List<Mention> mentions;
    private final Optional<Quote> quote;
    private final Optional<Sticker> sticker;
    private final List<Preview> previews;
    private final Optional<StoryReply> storyReply;

    public Message(@JsonProperty("messageText") String messageText,
            @JsonProperty("attachments") List<String> attachments, @JsonProperty("mentions") List<Mention> mentions,
            @JsonProperty("quote") Optional<Quote> quote, @JsonProperty("sticker") Optional<Sticker> sticker,
            @JsonProperty("previews") List<Preview> previews,
            @JsonProperty("storyReply") Optional<StoryReply> storyReply) {
        super();
        this.messageText = messageText;
        this.attachments = attachments;
        this.mentions = mentions;
        this.quote = quote;
        this.sticker = sticker;
        this.previews = previews;
        this.storyReply = storyReply;
    }

    public static class Mention {
        private final RecipientIdentifier.Single recipient;
        private final int start;
        private final int length;

        public Mention(@JsonProperty("recipient") Single recipient, @JsonProperty("start") int start,
                @JsonProperty("length") int length) {
            super();
            this.recipient = recipient;
            this.start = start;
            this.length = length;
        }

        public RecipientIdentifier.Single recipient() {
            return recipient;
        }

        public int start() {
            return start;
        }

        public int length() {
            return length;
        }
    }

    public class Quote {
        private final long timestamp;
        private final RecipientIdentifier.Single author;
        private final String message;
        private final List<Mention> mentions;

        public Quote(@JsonProperty("timestamp") long timestamp, @JsonProperty("author") Single author,
                @JsonProperty("message") String message, @JsonProperty("mentions") List<Mention> mentions) {
            super();
            this.timestamp = timestamp;
            this.author = author;
            this.message = message;
            this.mentions = mentions;
        }

        public long timestamp() {
            return timestamp;
        }

        public RecipientIdentifier.Single author() {
            return author;
        }

        public String message() {
            return message;
        }

        public List<Mention> mentions() {
            return mentions;
        }
    }

    public static class Sticker {
        private final byte[] packId;
        private final int stickerId;

        public Sticker(@JsonProperty("packId") byte[] packId, @JsonProperty("stickerId") int stickerId) {
            super();
            this.packId = packId;
            this.stickerId = stickerId;
        }

        public byte[] packId() {
            return packId;
        }

        public int stickerId() {
            return stickerId;
        }
    }

    public static class Preview {
        private final String url;
        private final String title;
        private final String description;
        private final Optional<String> image;

        public Preview(@JsonProperty("url") String url, @JsonProperty("title") String title,
                @JsonProperty("description") String description, @JsonProperty("image") Optional<String> image) {
            super();
            this.url = url;
            this.title = title;
            this.description = description;
            this.image = image;
        }

        public String url() {
            return url;
        }

        public String title() {
            return title;
        }

        public String description() {
            return description;
        }

        public Optional<String> image() {
            return image;
        }

    }

    public static class StoryReply {
        long timestamp;
        RecipientIdentifier.Single author;

        public StoryReply(@JsonProperty("timestamp") long timestamp, @JsonProperty("author") Single author) {
            super();
            this.timestamp = timestamp;
            this.author = author;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public long timestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public RecipientIdentifier.Single getAuthor() {
            return author;
        }

        public RecipientIdentifier.Single author() {
            return author;
        }

        public void setAuthor(RecipientIdentifier.Single author) {
            this.author = author;
        }

    }

    public String messageText() {
        return messageText;
    }

    public List<String> attachments() {
        return attachments;
    }

    public List<Mention> mentions() {
        return mentions;
    }

    public Optional<Quote> quote() {
        return quote;
    }

    public Optional<Sticker> sticker() {
        return sticker;
    }

    public List<Preview> previews() {
        return previews;
    }

    public Optional<StoryReply> getStoryReply() {
        return storyReply;
    }

    public Optional<StoryReply> storyReply() {
        return storyReply;
    }
}
