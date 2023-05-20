package org.asamk.signal.manager.api;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.asamk.signal.manager.api.MessageEnvelope.Data.Attachment;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupUtils;
import org.asamk.signal.manager.helper.RecipientAddressResolver;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.signal.libsignal.metadata.ProtocolException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupContext;
import org.whispersystems.signalservice.api.messages.SignalServicePreview;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceStoryMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceTextAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.OpaqueMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.MessageRequestResponseMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ViewOnceOpenMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ViewedMessage;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.BodyRange;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MessageEnvelope {

    public MessageEnvelope(@JsonProperty("sourceAddress") Optional<RecipientAddress> sourceAddress,
            @JsonProperty("sourceDevice") int sourceDevice, @JsonProperty("timestamp") long timestamp,
            @JsonProperty("serverReceivedTimestamp") long serverReceivedTimestamp,
            @JsonProperty("serverDeliveredTimestamp") long serverDeliveredTimestamp,
            @JsonProperty("isUnidentifiedSender") boolean isUnidentifiedSender,
            @JsonProperty("receipt") Optional<Receipt> receipt, @JsonProperty("typing") Optional<Typing> typing,
            @JsonProperty("data") Optional<Data> data, @JsonProperty("sync") Optional<Sync> sync,
            @JsonProperty("call") Optional<Call> call, @JsonProperty("story") Optional<Story> story) {
        super();
        this.sourceAddress = sourceAddress;
        this.sourceDevice = sourceDevice;
        this.timestamp = timestamp;
        this.serverReceivedTimestamp = serverReceivedTimestamp;
        this.serverDeliveredTimestamp = serverDeliveredTimestamp;
        this.isUnidentifiedSender = isUnidentifiedSender;
        this.receipt = receipt;
        this.typing = typing;
        this.data = data;
        this.sync = sync;
        this.call = call;
        this.story = story;
    }

    private final Optional<RecipientAddress> sourceAddress;
    private final int sourceDevice;
    private final long timestamp;
    private final long serverReceivedTimestamp;
    private final long serverDeliveredTimestamp;
    private final boolean isUnidentifiedSender;
    private final Optional<Receipt> receipt;
    private final Optional<Typing> typing;
    private final Optional<Data> data;
    private final Optional<Sync> sync;
    private final Optional<Call> call;
    private final Optional<Story> story;

    public static class Receipt {

        private final long when;
        private final Type type;
        private final List<Long> timestamps;

        public Receipt(@JsonProperty("when") long when, @JsonProperty("type") Type type,
                @JsonProperty("timestamps") List<Long> timestamps) {
            super();
            this.when = when;
            this.type = type;
            this.timestamps = timestamps;
        }

        static Receipt from(final SignalServiceReceiptMessage receiptMessage) {
            return new Receipt(receiptMessage.getWhen(), Type.from(receiptMessage.getType()),
                    receiptMessage.getTimestamps());
        }

        public enum Type {
            DELIVERY,
            READ,
            VIEWED,
            UNKNOWN;

            static Type from(SignalServiceReceiptMessage.Type type) {
                switch (type) {
                    case DELIVERY:
                        return DELIVERY;
                    case READ:
                        return READ;
                    case VIEWED:
                        return VIEWED;
                    case UNKNOWN:
                    default:
                        return UNKNOWN;
                }
            }
        }

        public long when() {
            return when;
        }

        public Type type() {
            return type;
        }

        public List<Long> timestamps() {
            return timestamps;
        }
    }

    public static class Typing {
        private final long timestamp;
        private final Type type;
        private final Optional<GroupId> groupId;

        public Typing(@JsonProperty("timestamp") long timestamp, @JsonProperty("type") Type type,
                @JsonProperty("groupId") Optional<GroupId> groupId) {
            super();
            this.timestamp = timestamp;
            this.type = type;
            this.groupId = groupId;
        }

        public static Typing from(final SignalServiceTypingMessage typingMessage) {
            return new Typing(typingMessage.getTimestamp(),
                    typingMessage.isTypingStarted() ? Type.STARTED : Type.STOPPED,
                    typingMessage.getGroupId().map(GroupId::unknownVersion));
        }

        public enum Type {
            STARTED,
            STOPPED,
        }

        public long timestamp() {
            return timestamp;
        }

        public Type type() {
            return type;
        }

        public Optional<GroupId> groupId() {
            return groupId;
        }
    }

    public static class Data {

        private final long timestamp;
        private final Optional<GroupContext> groupContext;
        private final Optional<StoryContext> storyContext;
        private final Optional<GroupCallUpdate> groupCallUpdate;
        private final Optional<String> body;
        private final int expiresInSeconds;
        private final boolean isExpirationUpdate;
        private final boolean isViewOnce;
        private final boolean isEndSession;
        private final boolean isProfileKeyUpdate;
        private final boolean hasProfileKey;
        private final Optional<Reaction> reaction;
        private final Optional<Quote> quote;
        private final Optional<Payment> payment;
        private final List<Attachment> attachments;
        private final Optional<Long> remoteDeleteId;
        private final Optional<Sticker> sticker;
        private final List<SharedContact> sharedContacts;
        private final List<Mention> mentions;
        private final List<Preview> previews;
        private final List<TextStyle> textStyles;

        public Data(@JsonProperty("timestamp") long timestamp,
                @JsonProperty("groupContext") Optional<GroupContext> groupContext,
                @JsonProperty("storyContext") Optional<StoryContext> storyContext,
                @JsonProperty("groupCallUpdate") Optional<GroupCallUpdate> groupCallUpdate,
                @JsonProperty("body") Optional<String> body, @JsonProperty("expiresInSeconds") int expiresInSeconds,
                @JsonProperty("isExpirationUpdate") boolean isExpirationUpdate,
                @JsonProperty("isViewOnce") boolean isViewOnce, @JsonProperty("isEndSession") boolean isEndSession,
                @JsonProperty("isProfileKeyUpdate") boolean isProfileKeyUpdate,
                @JsonProperty("hasProfileKey") boolean hasProfileKey,
                @JsonProperty("reaction") Optional<Reaction> reaction, @JsonProperty("quote") Optional<Quote> quote,
                @JsonProperty("payment") Optional<Payment> payment,
                @JsonProperty("attachments") List<Attachment> attachments,
                @JsonProperty("remoteDeleteId") Optional<Long> remoteDeleteId,
                @JsonProperty("sticker") Optional<Sticker> sticker,
                @JsonProperty("sharedContacts") List<SharedContact> sharedContacts,
                @JsonProperty("mentions") List<Mention> mentions, @JsonProperty("previews") List<Preview> previews,
                @JsonProperty("textStyles") List<TextStyle> textStyles) {
            super();
            this.timestamp = timestamp;
            this.groupContext = groupContext;
            this.storyContext = storyContext;
            this.groupCallUpdate = groupCallUpdate;
            this.body = body;
            this.expiresInSeconds = expiresInSeconds;
            this.isExpirationUpdate = isExpirationUpdate;
            this.isViewOnce = isViewOnce;
            this.isEndSession = isEndSession;
            this.isProfileKeyUpdate = isProfileKeyUpdate;
            this.hasProfileKey = hasProfileKey;
            this.reaction = reaction;
            this.quote = quote;
            this.payment = payment;
            this.attachments = attachments;
            this.remoteDeleteId = remoteDeleteId;
            this.sticker = sticker;
            this.sharedContacts = sharedContacts;
            this.mentions = mentions;
            this.previews = previews;
            this.textStyles = textStyles;
        }

        static Data from(final SignalServiceDataMessage dataMessage, RecipientResolver recipientResolver,
                RecipientAddressResolver addressResolver, final AttachmentFileProvider fileProvider) {

            return new Data(dataMessage.getTimestamp(), dataMessage.getGroupContext().map(GroupContext::from),
                    dataMessage.getStoryContext()
                            .map((SignalServiceDataMessage.StoryContext storyContext) -> StoryContext.from(storyContext,
                                    recipientResolver, addressResolver)),
                    dataMessage.getGroupCallUpdate().map(GroupCallUpdate::from), dataMessage.getBody(),
                    dataMessage.getExpiresInSeconds(), dataMessage.isExpirationUpdate(), dataMessage.isViewOnce(),
                    dataMessage.isEndSession(), dataMessage.isProfileKeyUpdate(),
                    dataMessage.getProfileKey().isPresent(),
                    dataMessage.getReaction().map(r -> Reaction.from(r, recipientResolver, addressResolver)),
                    dataMessage.getQuote().map(q -> Quote.from(q, recipientResolver, addressResolver, fileProvider)),
                    dataMessage.getPayment().map(p -> p.getPaymentNotification().isPresent() ? Payment.from(p) : null),
                    dataMessage.getAttachments()
                            .map(a -> a.stream().map(as -> Attachment.from(as, fileProvider))
                                    .collect(Collectors.toList()))
                            .orElse(List.of()),
                    dataMessage.getRemoteDelete().map(SignalServiceDataMessage.RemoteDelete::getTargetSentTimestamp),
                    dataMessage.getSticker().map(Sticker::from),
                    dataMessage.getSharedContacts()
                            .map(a -> a.stream().map(sharedContact -> SharedContact.from(sharedContact, fileProvider))
                                    .collect(Collectors.toList()))
                            .orElse(List.of()),
                    dataMessage.getMentions()
                            .map(a -> a.stream().map(m -> Mention.from(m, recipientResolver, addressResolver))
                                    .collect(Collectors.toList()))
                            .orElse(List.of()),
                    dataMessage.getPreviews()
                            .map(a -> a.stream().map(preview -> Preview.from(preview, fileProvider))
                                    .collect(Collectors.toList()))
                            .orElse(List.of()),
                    dataMessage.getBodyRanges().map(a -> a.stream().filter(BodyRange::hasStyle).map(TextStyle::from)
                            .collect(Collectors.toList())).orElse(List.of()));
        }

        public static class GroupContext {

            private final GroupId groupId;
            private final boolean isGroupUpdate;
            private final int revision;

            public GroupContext(@JsonProperty("groupId") GroupId groupId,
                    @JsonProperty("isGroupUpdate") boolean isGroupUpdate, @JsonProperty("revision") int revision) {
                super();
                this.groupId = groupId;
                this.isGroupUpdate = isGroupUpdate;
                this.revision = revision;
            }

            static GroupContext from(SignalServiceGroupContext groupContext) {
                if (groupContext.getGroupV1().isPresent()) {
                    return new GroupContext(GroupId.v1(groupContext.getGroupV1().get().getGroupId()),
                            groupContext.getGroupV1Type() == SignalServiceGroup.Type.UPDATE, 0);
                } else if (groupContext.getGroupV2().isPresent()) {
                    final var groupV2 = groupContext.getGroupV2().get();
                    return new GroupContext(GroupUtils.getGroupIdV2(groupV2.getMasterKey()),
                            groupV2.hasSignedGroupChange(), groupV2.getRevision());
                } else {
                    throw new RuntimeException("Invalid group context state");
                }
            }

            public GroupId groupId() {
                return groupId;
            }

            public boolean isGroupUpdate() {
                return isGroupUpdate;
            }

            public int revision() {
                return revision;
            }
        }

        public static class StoryContext {
            private final RecipientAddress author;
            private final long sentTimestamp;

            public StoryContext(@JsonProperty("author") RecipientAddress author,
                    @JsonProperty("sentTimestamp") long sentTimestamp) {
                super();
                this.author = author;
                this.sentTimestamp = sentTimestamp;
            }

            static StoryContext from(SignalServiceDataMessage.StoryContext storyContext,
                    RecipientResolver recipientResolver, RecipientAddressResolver addressResolver) {
                return new StoryContext(addressResolver
                        .resolveRecipientAddress(recipientResolver.resolveRecipient(storyContext.getAuthorServiceId()))
                        .toApiRecipientAddress(), storyContext.getSentTimestamp());
            }

            public RecipientAddress author() {
                return author;
            }

            public long sentTimestamp() {
                return sentTimestamp;
            }
        }

        public static class GroupCallUpdate {
            private final String eraId;

            public GroupCallUpdate(@JsonProperty("eraId") String eraId) {
                super();
                this.eraId = eraId;
            }

            static GroupCallUpdate from(SignalServiceDataMessage.GroupCallUpdate groupCallUpdate) {
                return new GroupCallUpdate(groupCallUpdate.getEraId());
            }

            public String eraId() {
                return eraId;
            }
        }

        public static class Reaction {

            private final long targetSentTimestamp;
            private final RecipientAddress targetAuthor;
            private final String emoji;
            private final boolean isRemove;

            public Reaction(@JsonProperty("targetSentTimestamp") long targetSentTimestamp,
                    @JsonProperty("targetAuthor") RecipientAddress targetAuthor, @JsonProperty("emoji") String emoji,
                    @JsonProperty("isRemove") boolean isRemove) {
                super();
                this.targetSentTimestamp = targetSentTimestamp;
                this.targetAuthor = targetAuthor;
                this.emoji = emoji;
                this.isRemove = isRemove;
            }

            static Reaction from(SignalServiceDataMessage.Reaction reaction, RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver) {
                return new Reaction(reaction.getTargetSentTimestamp(),
                        addressResolver
                                .resolveRecipientAddress(recipientResolver.resolveRecipient(reaction.getTargetAuthor()))
                                .toApiRecipientAddress(),
                        reaction.getEmoji(), reaction.isRemove());
            }

            public long targetSentTimestamp() {
                return targetSentTimestamp;
            }

            public RecipientAddress targetAuthor() {
                return targetAuthor;
            }

            public String emoji() {
                return emoji;
            }

            public boolean isRemove() {
                return isRemove;
            }
        }

        public static class Quote {
            private final long id;
            private final RecipientAddress author;
            private final Optional<String> text;
            private final List<Mention> mentions;
            private final List<Attachment> attachments;
            private final List<TextStyle> textStyles;

            public Quote(@JsonProperty("id") long id, @JsonProperty("author") RecipientAddress author,
                    @JsonProperty("text") Optional<String> text, @JsonProperty("mentions") List<Mention> mentions,
                    @JsonProperty("attachments") List<Attachment> attachments,
                    @JsonProperty("textStyles") List<TextStyle> textStyles) {
                super();
                this.id = id;
                this.author = author;
                this.text = text;
                this.mentions = mentions;
                this.attachments = attachments;
                this.textStyles = textStyles;
            }

            static Quote from(SignalServiceDataMessage.Quote quote, RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver, final AttachmentFileProvider fileProvider) {
                return new Quote(quote.getId(),
                        addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(quote.getAuthor()))
                                .toApiRecipientAddress(),
                        Optional.ofNullable(quote.getText()),
                        quote.getMentions() == null ? List.of()
                                : quote.getMentions().stream()
                                        .map(m -> Mention.from(m, recipientResolver, addressResolver))
                                        .collect(Collectors.toList()),
                        quote.getAttachments() == null ? List.of()
                                : quote.getAttachments().stream().map(a -> Attachment.from(a, fileProvider))
                                        .collect(Collectors.toList()),
                        quote.getBodyRanges() == null ? List.of()
                                : quote.getBodyRanges().stream().filter(BodyRange::hasStyle).map(TextStyle::from)
                                        .collect(Collectors.toList()));
            }

            public long id() {
                return id;
            }

            public RecipientAddress author() {
                return author;
            }

            public Optional<String> text() {
                return text;
            }

            public List<Mention> mentions() {
                return mentions;
            }

            public List<Attachment> attachments() {
                return attachments;
            }
        }

        public static class Payment {

            private final String note;
            private final byte[] receipt;

            public Payment(@JsonProperty("note") String note, @JsonProperty("receipt") byte[] receipt) {
                super();
                this.note = note;
                this.receipt = receipt;
            }

            static Payment from(SignalServiceDataMessage.Payment payment) {
                return new Payment(payment.getPaymentNotification().get().getNote(),
                        payment.getPaymentNotification().get().getReceipt());
            }

            public String note() {
                return note;
            }

            public byte[] getReceipt() {
                return receipt;
            }
        }

        public static class Mention {
            private final RecipientAddress recipient;
            private final int start;
            private final int length;

            public Mention(@JsonProperty("recipient") RecipientAddress recipient, @JsonProperty("start") int start,
                    @JsonProperty("length") int length) {
                super();
                this.recipient = recipient;
                this.start = start;
                this.length = length;
            }

            static Mention from(SignalServiceDataMessage.Mention mention, RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver) {
                return new Mention(addressResolver
                        .resolveRecipientAddress(recipientResolver.resolveRecipient(mention.getServiceId()))
                        .toApiRecipientAddress(), mention.getStart(), mention.getLength());
            }

            public RecipientAddress recipient() {
                return recipient;
            }

            public int start() {
                return start;
            }

            public int length() {
                return length;
            }
        }

        public static class Attachment {
            private final Optional<String> id;
            private final Optional<File> file;
            private final Optional<String> fileName;
            private final String contentType;
            private final Optional<Long> uploadTimestamp;
            private final Optional<Long> size;
            private final Optional<byte[]> preview;
            private final Optional<Attachment> thumbnail;
            private final Optional<String> caption;
            private final Optional<Integer> width;
            private final Optional<Integer> height;
            private final boolean isVoiceNote;
            private final boolean isGif;
            private final boolean isBorderless;

            public Attachment(@JsonProperty("id") Optional<String> id, @JsonProperty("file") Optional<File> file,
                    @JsonProperty("fileName") Optional<String> fileName,
                    @JsonProperty("contentType") String contentType,
                    @JsonProperty("uploadTimestamp") Optional<Long> uploadTimestamp,
                    @JsonProperty("size") Optional<Long> size, @JsonProperty("preview") Optional<byte[]> preview,
                    @JsonProperty("thumbnail") Optional<Attachment> thumbnail,
                    @JsonProperty("caption") Optional<String> caption, @JsonProperty("width") Optional<Integer> width,
                    @JsonProperty("height") Optional<Integer> height, @JsonProperty("isVoiceNote") boolean isVoiceNote,
                    @JsonProperty("isGif") boolean isGif, @JsonProperty("isBorderless") boolean isBorderless) {
                super();
                this.id = id;
                this.file = file;
                this.fileName = fileName;
                this.contentType = contentType;
                this.uploadTimestamp = uploadTimestamp;
                this.size = size;
                this.preview = preview;
                this.thumbnail = thumbnail;
                this.caption = caption;
                this.width = width;
                this.height = height;
                this.isVoiceNote = isVoiceNote;
                this.isGif = isGif;
                this.isBorderless = isBorderless;
            }

            @SuppressWarnings("null")
            static Attachment from(SignalServiceAttachment attachment, AttachmentFileProvider fileProvider) {
                if (attachment.isPointer()) {
                    final var a = attachment.asPointer();
                    final var attachmentFile = fileProvider.getFile(a);
                    return new Attachment(Optional.of(attachmentFile.getName()), Optional.of(attachmentFile),
                            a.getFileName(), a.getContentType(),
                            a.getUploadTimestamp() == 0 ? Optional.empty() : Optional.of(a.getUploadTimestamp()),
                            a.getSize().map(Integer::longValue), a.getPreview(), Optional.empty(),
                            a.getCaption().map(c -> c.isEmpty() ? null : c),
                            a.getWidth() == 0 ? Optional.empty() : Optional.of(a.getWidth()),
                            a.getHeight() == 0 ? Optional.empty() : Optional.of(a.getHeight()), a.getVoiceNote(),
                            a.isGif(), a.isBorderless());
                } else {
                    final var a = attachment.asStream();
                    return new Attachment(Optional.empty(), Optional.empty(), a.getFileName(), a.getContentType(),
                            a.getUploadTimestamp() == 0 ? Optional.empty() : Optional.of(a.getUploadTimestamp()),
                            Optional.of(a.getLength()), a.getPreview(), Optional.empty(), a.getCaption(),
                            a.getWidth() == 0 ? Optional.empty() : Optional.of(a.getWidth()),
                            a.getHeight() == 0 ? Optional.empty() : Optional.of(a.getHeight()), a.getVoiceNote(),
                            a.isGif(), a.isBorderless());
                }
            }

            static Attachment from(SignalServiceDataMessage.Quote.QuotedAttachment a,
                    final AttachmentFileProvider fileProvider) {
                return new Attachment(Optional.empty(), Optional.empty(), Optional.ofNullable(a.getFileName()),
                        a.getContentType(), Optional.empty(), Optional.empty(), Optional.empty(),
                        a.getThumbnail() == null ? Optional.empty()
                                : Optional.of(Attachment.from(a.getThumbnail(), fileProvider)),
                        Optional.empty(), Optional.empty(), Optional.empty(), false, false, false);
            }

            public Optional<String> id() {
                return id;
            }

            public Optional<File> file() {
                return file;
            }

            public Optional<String> fileName() {
                return fileName;
            }

            public String contentType() {
                return contentType;
            }

            public Optional<Long> uploadTimestamp() {
                return uploadTimestamp;
            }

            public Optional<Long> size() {
                return size;
            }

            public Optional<byte[]> getPreview() {
                return preview;
            }

            public Optional<Attachment> thumbnail() {
                return thumbnail;
            }

            public Optional<String> caption() {
                return caption;
            }

            public Optional<Integer> width() {
                return width;
            }

            public Optional<Integer> height() {
                return height;
            }

            public boolean isVoiceNote() {
                return isVoiceNote;
            }

            public boolean isGif() {
                return isGif;
            }

            public boolean isBorderless() {
                return isBorderless;
            }
        }

        public static class Sticker {

            private final StickerPackId packId;
            private final byte[] packKey;
            private final int stickerId;

            public Sticker(@JsonProperty("packId") StickerPackId packId, @JsonProperty("packKey") byte[] packKey,
                    @JsonProperty("stickerId") int stickerId) {
                super();
                this.packId = packId;
                this.packKey = packKey;
                this.stickerId = stickerId;
            }

            static Sticker from(SignalServiceDataMessage.Sticker sticker) {
                return new Sticker(StickerPackId.deserialize(sticker.getPackId()), sticker.getPackKey(),
                        sticker.getStickerId());
            }

            public StickerPackId packId() {
                return packId;
            }

            public byte[] getPackKey() {
                return packKey;
            }

            public int stickerId() {
                return stickerId;
            }
        }

        public static class SharedContact {
            private final Name name;
            private final Optional<Avatar> avatar;
            private final List<Phone> phone;
            private final List<Email> email;
            private final List<Address> address;
            private final Optional<String> organization;

            public SharedContact(@JsonProperty("name") Name name, @JsonProperty("avatar") Optional<Avatar> avatar,
                    @JsonProperty("phone") List<Phone> phone, @JsonProperty("email") List<Email> email,
                    @JsonProperty("address") List<Address> address,
                    @JsonProperty("organization") Optional<String> organization) {
                super();
                this.name = name;
                this.avatar = avatar;
                this.phone = phone;
                this.email = email;
                this.address = address;
                this.organization = organization;
            }

            static SharedContact from(org.whispersystems.signalservice.api.messages.shared.SharedContact sharedContact,
                    final AttachmentFileProvider fileProvider) {
                return new SharedContact(Name.from(sharedContact.getName()),
                        sharedContact.getAvatar().map(avatar1 -> Avatar.from(avatar1, fileProvider)),
                        sharedContact.getPhone().map(p -> p.stream().map(Phone::from).collect(Collectors.toList()))
                                .orElse(List.of()),
                        sharedContact.getEmail().map(p -> p.stream().map(Email::from).collect(Collectors.toList()))
                                .orElse(List.of()),
                        sharedContact.getAddress().map(p -> p.stream().map(Address::from).collect(Collectors.toList()))
                                .orElse(List.of()),
                        sharedContact.getOrganization());
            }

            public static class Name {
                private final Optional<String> display;
                private final Optional<String> given;
                private final Optional<String> family;
                private final Optional<String> prefix;
                private final Optional<String> suffix;
                private final Optional<String> middle;

                public Name(@JsonProperty("display") Optional<String> display,
                        @JsonProperty("given") Optional<String> given, @JsonProperty("family") Optional<String> family,
                        @JsonProperty("prefix") Optional<String> prefix,
                        @JsonProperty("suffix") Optional<String> suffix,
                        @JsonProperty("middle") Optional<String> middle) {
                    super();
                    this.display = display;
                    this.given = given;
                    this.family = family;
                    this.prefix = prefix;
                    this.suffix = suffix;
                    this.middle = middle;
                }

                static Name from(org.whispersystems.signalservice.api.messages.shared.SharedContact.Name name) {
                    return new Name(name.getDisplay(), name.getGiven(), name.getFamily(), name.getPrefix(),
                            name.getSuffix(), name.getMiddle());
                }

                public Optional<String> display() {
                    return display;
                }

                public Optional<String> given() {
                    return given;
                }

                public Optional<String> family() {
                    return family;
                }

                public Optional<String> prefix() {
                    return prefix;
                }

                public Optional<String> suffix() {
                    return suffix;
                }

                public Optional<String> middle() {
                    return middle;
                }
            }

            public static class Avatar {

                private final Attachment attachment;
                private final boolean isProfile;

                public Avatar(@JsonProperty("attachment") Attachment attachment,
                        @JsonProperty("isProfile") boolean isProfile) {
                    super();
                    this.attachment = attachment;
                    this.isProfile = isProfile;
                }

                static Avatar from(org.whispersystems.signalservice.api.messages.shared.SharedContact.Avatar avatar,
                        final AttachmentFileProvider fileProvider) {
                    return new Avatar(Attachment.from(avatar.getAttachment(), fileProvider), avatar.isProfile());
                }

                public Attachment attachment() {
                    return attachment;
                }

                public boolean isProfile() {
                    return isProfile;
                }
            }

            public static class Phone {
                private final String value;
                private final Type type;
                private final Optional<String> label;

                public Phone(@JsonProperty("value") String value, @JsonProperty("type") Type type,
                        @JsonProperty("label") Optional<String> label) {
                    super();
                    this.value = value;
                    this.type = type;
                    this.label = label;
                }

                static Phone from(org.whispersystems.signalservice.api.messages.shared.SharedContact.Phone phone) {
                    return new Phone(phone.getValue(), Type.from(phone.getType()), phone.getLabel());
                }

                public enum Type {
                    HOME,
                    WORK,
                    MOBILE,
                    CUSTOM;

                    static Type from(
                            org.whispersystems.signalservice.api.messages.shared.SharedContact.Phone.Type type) {
                        switch (type) {
                            case HOME:
                                return HOME;
                            case WORK:
                                return WORK;
                            case MOBILE:
                                return MOBILE;
                            case CUSTOM:
                            default:
                                return CUSTOM;
                        }
                    }
                }

                public String value() {
                    return value;
                }

                public Type type() {
                    return type;
                }

                public Optional<String> label() {
                    return label;
                }
            }

            public static class Email {

                private final String value;
                private final Type type;
                private final Optional<String> label;

                public Email(@JsonProperty("value") String value, @JsonProperty("type") Type type,
                        @JsonProperty("label") Optional<String> label) {
                    super();
                    this.value = value;
                    this.type = type;
                    this.label = label;
                }

                static Email from(org.whispersystems.signalservice.api.messages.shared.SharedContact.Email email) {
                    return new Email(email.getValue(), Type.from(email.getType()), email.getLabel());
                }

                public enum Type {
                    HOME,
                    WORK,
                    MOBILE,
                    CUSTOM;

                    static Type from(
                            org.whispersystems.signalservice.api.messages.shared.SharedContact.Email.Type type) {
                        switch (type) {
                            case HOME:
                                return HOME;
                            case WORK:
                                return WORK;
                            case MOBILE:
                                return MOBILE;
                            case CUSTOM:
                            default:
                                return CUSTOM;
                        }
                    }
                }

                public String value() {
                    return value;
                }

                public Type type() {
                    return type;
                }

                public Optional<String> label() {
                    return label;
                }
            }

            public static class Address {
                private final Type type;
                private final Optional<String> label;
                private final Optional<String> street;
                private final Optional<String> pobox;
                private final Optional<String> neighborhood;
                private final Optional<String> city;
                private final Optional<String> region;
                private final Optional<String> postcode;
                private final Optional<String> country;

                public Address(@JsonProperty("type") Type type, @JsonProperty("label") Optional<String> label,
                        @JsonProperty("street") Optional<String> street, @JsonProperty("pobox") Optional<String> pobox,
                        @JsonProperty("neighborhood") Optional<String> neighborhood,
                        @JsonProperty("city") Optional<String> city, @JsonProperty("region") Optional<String> region,
                        @JsonProperty("postcode") Optional<String> postcode,
                        @JsonProperty("country") Optional<String> country) {
                    super();
                    this.type = type;
                    this.label = label;
                    this.street = street;
                    this.pobox = pobox;
                    this.neighborhood = neighborhood;
                    this.city = city;
                    this.region = region;
                    this.postcode = postcode;
                    this.country = country;
                }

                static Address from(
                        org.whispersystems.signalservice.api.messages.shared.SharedContact.PostalAddress address) {
                    return new Address(Address.Type.from(address.getType()), address.getLabel(), address.getLabel(),
                            address.getLabel(), address.getLabel(), address.getLabel(), address.getLabel(),
                            address.getLabel(), address.getLabel());
                }

                public enum Type {
                    HOME,
                    WORK,
                    CUSTOM;

                    static Type from(
                            org.whispersystems.signalservice.api.messages.shared.SharedContact.PostalAddress.Type type) {
                        switch (type) {
                            case HOME:
                                return HOME;
                            case WORK:
                                return WORK;
                            case CUSTOM:
                            default:
                                return CUSTOM;
                        }
                    }
                }

                public Type type() {
                    return type;
                }

                public Optional<String> label() {
                    return label;
                }

                public Optional<String> street() {
                    return street;
                }

                public Optional<String> pobox() {
                    return pobox;
                }

                public Optional<String> neighborhood() {
                    return neighborhood;
                }

                public Optional<String> city() {
                    return city;
                }

                public Optional<String> region() {
                    return region;
                }

                public Optional<String> postcode() {
                    return postcode;
                }

                public Optional<String> country() {
                    return country;
                }
            }

            public Name name() {
                return name;
            }

            public Optional<Avatar> avatar() {
                return avatar;
            }

            public List<Phone> phone() {
                return phone;
            }

            public List<Email> email() {
                return email;
            }

            public List<Address> address() {
                return address;
            }

            public Optional<String> organization() {
                return organization;
            }
        }

        public static class Preview {

            private final String title;
            private final String description;
            private final long date;
            private final String url;
            private final Optional<Attachment> image;

            public Preview(@JsonProperty("title") String title, @JsonProperty("description") String description,
                    @JsonProperty("date") long date, @JsonProperty("url") String url,
                    @JsonProperty("image") Optional<Attachment> image) {
                super();
                this.title = title;
                this.description = description;
                this.date = date;
                this.url = url;
                this.image = image;
            }

            static Preview from(SignalServicePreview preview, final AttachmentFileProvider fileProvider) {
                return new Preview(preview.getTitle(), preview.getDescription(), preview.getDate(), preview.getUrl(),
                        preview.getImage().map(as -> Attachment.from(as, fileProvider)));
            }

            public String title() {
                return title;
            }

            public String description() {
                return description;
            }

            public long date() {
                return date;
            }

            public String url() {
                return url;
            }

            public Optional<Attachment> image() {
                return image;
            }
        }

        public static class TextStyle {

            Style style;
            int start;
            int length;

            public TextStyle(@JsonProperty("style") Style style, @JsonProperty("start") int start,
                    @JsonProperty("length") int length) {
                this.style = style;
                this.start = start;
                this.length = length;
            }

            public enum Style {
                NONE,
                BOLD,
                ITALIC,
                SPOILER,
                STRIKETHROUGH,
                MONOSPACE;

                static Style from(BodyRange.Style style) {
                    switch (style) {
                        case NONE:
                            return NONE;
                        case BOLD:
                            return BOLD;
                        case ITALIC:
                            return ITALIC;
                        case SPOILER:
                            return SPOILER;
                        case STRIKETHROUGH:
                            return STRIKETHROUGH;
                        case MONOSPACE:
                            return MONOSPACE;
                    }
                    return null;
                }
            }

            static TextStyle from(BodyRange bodyRange) {
                return new TextStyle(Style.from(bodyRange.getStyle()), bodyRange.getStart(), bodyRange.getLength());
            }

            public Style getStyle() {
                return style;
            }

            public int getStart() {
                return start;
            }

            public int getLength() {
                return length;
            }

        }

        public long getTimestamp() {
            return timestamp;
        }

        public Optional<GroupContext> getGroupContext() {
            return groupContext;
        }

        public Optional<StoryContext> getStoryContext() {
            return storyContext;
        }

        public Optional<GroupCallUpdate> getGroupCallUpdate() {
            return groupCallUpdate;
        }

        public Optional<String> getBody() {
            return body;
        }

        public int getExpiresInSeconds() {
            return expiresInSeconds;
        }

        public boolean isExpirationUpdate() {
            return isExpirationUpdate;
        }

        public boolean isViewOnce() {
            return isViewOnce;
        }

        public boolean isEndSession() {
            return isEndSession;
        }

        public boolean isProfileKeyUpdate() {
            return isProfileKeyUpdate;
        }

        public boolean isHasProfileKey() {
            return hasProfileKey;
        }

        public Optional<Reaction> getReaction() {
            return reaction;
        }

        public Optional<Quote> getQuote() {
            return quote;
        }

        public Optional<Payment> getPayment() {
            return payment;
        }

        public List<Attachment> getAttachments() {
            return attachments;
        }

        public Optional<Long> getRemoteDeleteId() {
            return remoteDeleteId;
        }

        public Optional<Sticker> getSticker() {
            return sticker;
        }

        public List<SharedContact> getSharedContacts() {
            return sharedContacts;
        }

        public List<Mention> getMentions() {
            return mentions;
        }

        public List<Preview> getPreviews() {
            return previews;
        }
    }

    public static class Sync {
        private final Optional<Sent> sent;
        private final Optional<Blocked> blocked;
        private final List<Read> read;
        private final List<Viewed> viewed;
        private final Optional<ViewOnceOpen> viewOnceOpen;
        private final Optional<Contacts> contacts;
        private final Optional<Groups> groups;
        private final Optional<MessageRequestResponse> messageRequestResponse;

        public Sync(@JsonProperty("sent") Optional<Sent> sent, @JsonProperty("blocked") Optional<Blocked> blocked,
                @JsonProperty("read") List<Read> read, @JsonProperty("viewed") List<Viewed> viewed,
                @JsonProperty("viewOnceOpen") Optional<ViewOnceOpen> viewOnceOpen,
                @JsonProperty("contacts") Optional<Contacts> contacts, @JsonProperty("groups") Optional<Groups> groups,
                @JsonProperty("messageRequestResponse") Optional<MessageRequestResponse> messageRequestResponse) {
            super();
            this.sent = sent;
            this.blocked = blocked;
            this.read = read;
            this.viewed = viewed;
            this.viewOnceOpen = viewOnceOpen;
            this.contacts = contacts;
            this.groups = groups;
            this.messageRequestResponse = messageRequestResponse;
        }

        public static Sync from(final SignalServiceSyncMessage syncMessage, RecipientResolver recipientResolver,
                RecipientAddressResolver addressResolver, final AttachmentFileProvider fileProvider) {
            return new Sync(
                    syncMessage.getSent().map(s -> Sent.from(s, recipientResolver, addressResolver, fileProvider)),
                    syncMessage.getBlockedList().map(b -> Blocked.from(b, recipientResolver, addressResolver)),
                    syncMessage.getRead()
                            .map(r -> r.stream().map(rm -> Read.from(rm, recipientResolver, addressResolver))
                                    .collect(Collectors.toList()))
                            .orElse(List.of()),
                    syncMessage.getViewed()
                            .map(r -> r.stream().map(rm -> Viewed.from(rm, recipientResolver, addressResolver))
                                    .collect(Collectors.toList()))
                            .orElse(List.of()),
                    syncMessage.getViewOnceOpen().map(rm -> ViewOnceOpen.from(rm, recipientResolver, addressResolver)),
                    syncMessage.getContacts().map(Contacts::from), syncMessage.getGroups().map(Groups::from),
                    syncMessage.getMessageRequestResponse()
                            .map(m -> MessageRequestResponse.from(m, recipientResolver, addressResolver)));
        }

        public static class Sent {
            private final long timestamp;
            private final long expirationStartTimestamp;
            private final Optional<RecipientAddress> destination;
            private final Set<RecipientAddress> recipients;
            private final Optional<Data> message;
            private final Optional<Story> story;

            public Sent(@JsonProperty("timestamp") long timestamp,
                    @JsonProperty("expirationStartTimestamp") long expirationStartTimestamp,
                    @JsonProperty("destination") Optional<RecipientAddress> destination,
                    @JsonProperty("recipients") Set<RecipientAddress> recipients,
                    @JsonProperty("message") Optional<Data> message, @JsonProperty("story") Optional<Story> story) {
                super();
                this.timestamp = timestamp;
                this.expirationStartTimestamp = expirationStartTimestamp;
                this.destination = destination;
                this.recipients = recipients;
                this.message = message;
                this.story = story;
            }

            static Sent from(SentTranscriptMessage sentMessage, RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver, final AttachmentFileProvider fileProvider) {
                return new Sent(sentMessage.getTimestamp(), sentMessage.getExpirationStartTimestamp(),
                        sentMessage.getDestination()
                                .map(d -> addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(d))
                                        .toApiRecipientAddress()),
                        sentMessage.getRecipients().stream()
                                .map(d -> addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(d))
                                        .toApiRecipientAddress())
                                .collect(Collectors.toSet()),
                        sentMessage.getDataMessage()
                                .map(message -> Data.from(message, recipientResolver, addressResolver, fileProvider)),
                        sentMessage.getStoryMessage().map(s -> Story.from(s, fileProvider)));
            }

            public long timestamp() {
                return timestamp;
            }

            public long expirationStartTimestamp() {
                return expirationStartTimestamp;
            }

            public Optional<RecipientAddress> destination() {
                return destination;
            }

            public Set<RecipientAddress> recipients() {
                return recipients;
            }

            public Optional<Data> message() {
                return message;
            }

            public Optional<Story> story() {
                return story;
            }
        }

        public static class Blocked {

            private final List<RecipientAddress> recipients;
            private final List<GroupId> groupIds;

            public Blocked(@JsonProperty("recipients") List<RecipientAddress> recipients,
                    @JsonProperty("groupIds") List<GroupId> groupIds) {
                super();
                this.recipients = recipients;
                this.groupIds = groupIds;
            }

            static Blocked from(BlockedListMessage blockedListMessage, RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver) {
                return new Blocked(
                        blockedListMessage.getAddresses().stream()
                                .map(d -> addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(d))
                                        .toApiRecipientAddress())
                                .collect(Collectors.toList()),
                        blockedListMessage.getGroupIds().stream().map(GroupId::unknownVersion)
                                .collect(Collectors.toList()));
            }

            public List<RecipientAddress> recipients() {
                return recipients;
            }

            public List<GroupId> groupIds() {
                return groupIds;
            }
        }

        public static class Read {

            private final RecipientAddress sender;
            private final long timestamp;

            public Read(@JsonProperty("sender") RecipientAddress sender, @JsonProperty("timestamp") long timestamp) {
                super();
                this.sender = sender;
                this.timestamp = timestamp;
            }

            static Read from(ReadMessage readMessage, RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver) {
                return new Read(addressResolver
                        .resolveRecipientAddress(recipientResolver.resolveRecipient(readMessage.getSender()))
                        .toApiRecipientAddress(), readMessage.getTimestamp());
            }

            public RecipientAddress sender() {
                return sender;
            }

            public long timestamp() {
                return timestamp;
            }
        }

        public static class Viewed {

            private final RecipientAddress sender;
            private final long timestamp;

            public Viewed(@JsonProperty("sender") RecipientAddress sender, @JsonProperty("timestamp") long timestamp) {
                super();
                this.sender = sender;
                this.timestamp = timestamp;
            }

            static Viewed from(ViewedMessage readMessage, RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver) {
                return new Viewed(addressResolver
                        .resolveRecipientAddress(recipientResolver.resolveRecipient(readMessage.getSender()))
                        .toApiRecipientAddress(), readMessage.getTimestamp());
            }

            public RecipientAddress sender() {
                return sender;
            }

            public long timestamp() {
                return timestamp;
            }
        }

        public static class ViewOnceOpen {
            private final RecipientAddress sender;
            private final long timestamp;

            public ViewOnceOpen(@JsonProperty("sender") RecipientAddress sender,
                    @JsonProperty("timestamp") long timestamp) {
                super();
                this.sender = sender;
                this.timestamp = timestamp;
            }

            static ViewOnceOpen from(ViewOnceOpenMessage readMessage, RecipientResolver recipientResolver,
                    RecipientAddressResolver addressResolver) {
                return new ViewOnceOpen(addressResolver
                        .resolveRecipientAddress(recipientResolver.resolveRecipient(readMessage.getSender()))
                        .toApiRecipientAddress(), readMessage.getTimestamp());
            }

            public RecipientAddress sender() {
                return sender;
            }

            public long timestamp() {
                return timestamp;
            }
        }

        public static class Contacts {
            private final boolean isComplete;

            public Contacts(@JsonProperty("isComplete") boolean isComplete) {
                super();
                this.isComplete = isComplete;
            }

            static Contacts from(ContactsMessage contactsMessage) {
                return new Contacts(contactsMessage.isComplete());
            }

            public boolean isComplete() {
                return isComplete;
            }
        }

        public static class Groups {

            static Groups from(SignalServiceAttachment groupsMessage) {
                return new Groups();
            }
        }

        public static class MessageRequestResponse {
            private final Type type;
            private final Optional<GroupId> groupId;
            private final Optional<RecipientAddress> person;

            public MessageRequestResponse(@JsonProperty("type") Type type,
                    @JsonProperty("groupId") Optional<GroupId> groupId,
                    @JsonProperty("person") Optional<RecipientAddress> person) {
                super();
                this.type = type;
                this.groupId = groupId;
                this.person = person;
            }

            static MessageRequestResponse from(MessageRequestResponseMessage messageRequestResponse,
                    RecipientResolver recipientResolver, RecipientAddressResolver addressResolver) {
                return new MessageRequestResponse(Type.from(messageRequestResponse.getType()),
                        messageRequestResponse.getGroupId().map(GroupId::unknownVersion),
                        messageRequestResponse.getPerson()
                                .map(p -> addressResolver.resolveRecipientAddress(recipientResolver.resolveRecipient(p))
                                        .toApiRecipientAddress()));
            }

            public enum Type {
                UNKNOWN,
                ACCEPT,
                DELETE,
                BLOCK,
                BLOCK_AND_DELETE,
                UNBLOCK_AND_ACCEPT;

                static Type from(MessageRequestResponseMessage.Type type) {
                    switch (type) {
                        case UNKNOWN:
                            return UNKNOWN;
                        case ACCEPT:
                            return ACCEPT;
                        case DELETE:
                            return DELETE;
                        case BLOCK:
                            return BLOCK;
                        case BLOCK_AND_DELETE:
                            return BLOCK_AND_DELETE;
                        case UNBLOCK_AND_ACCEPT:
                        default:
                            return UNBLOCK_AND_ACCEPT;
                    }
                }
            }

            public Type type() {
                return type;
            }

            public Optional<GroupId> groupId() {
                return groupId;
            }

            public Optional<RecipientAddress> person() {
                return person;
            }
        }

        public Optional<Sent> sent() {
            return sent;
        }

        public Optional<Blocked> blocked() {
            return blocked;
        }

        public List<Read> read() {
            return read;
        }

        public List<Viewed> viewed() {
            return viewed;
        }

        public Optional<ViewOnceOpen> viewOnceOpen() {
            return viewOnceOpen;
        }

        public Optional<Contacts> contacts() {
            return contacts;
        }

        public Optional<Groups> groups() {
            return groups;
        }

        public Optional<MessageRequestResponse> messageRequestResponse() {
            return messageRequestResponse;
        }
    }

    public static class Call {
        private final Optional<Integer> destinationDeviceId;
        private final Optional<GroupId> groupId;
        private final Optional<Long> timestamp;
        private final Optional<Offer> offer;
        private final Optional<Answer> answer;
        private final Optional<Hangup> hangup;
        private final Optional<Busy> busy;
        private final List<IceUpdate> iceUpdate;
        private final Optional<Opaque> opaque;

        public Call(@JsonProperty("destinationDeviceId") Optional<Integer> destinationDeviceId,
                @JsonProperty("groupId") Optional<GroupId> groupId, @JsonProperty("timestamp") Optional<Long> timestamp,
                @JsonProperty("offer") Optional<Offer> offer, @JsonProperty("answer") Optional<Answer> answer,
                @JsonProperty("hangup") Optional<Hangup> hangup, @JsonProperty("busy") Optional<Busy> busy,
                @JsonProperty("iceUpdate") List<IceUpdate> iceUpdate, @JsonProperty("opaque") Optional<Opaque> opaque) {
            super();
            this.destinationDeviceId = destinationDeviceId;
            this.groupId = groupId;
            this.timestamp = timestamp;
            this.offer = offer;
            this.answer = answer;
            this.hangup = hangup;
            this.busy = busy;
            this.iceUpdate = iceUpdate;
            this.opaque = opaque;
        }

        public static Call from(final SignalServiceCallMessage callMessage) {
            return new Call(callMessage.getDestinationDeviceId(), callMessage.getGroupId().map(GroupId::unknownVersion),
                    callMessage.getTimestamp(), callMessage.getOfferMessage().map(Offer::from),
                    callMessage.getAnswerMessage().map(Answer::from), callMessage.getHangupMessage().map(Hangup::from),
                    callMessage.getBusyMessage().map(Busy::from),
                    callMessage.getIceUpdateMessages()
                            .map(m -> m.stream().map(IceUpdate::from).collect(Collectors.toList())).orElse(List.of()),
                    callMessage.getOpaqueMessage().map(Opaque::from));
        }

        public static class Offer {

            private final long id;
            private final String sdp;
            private final Type type;
            private final byte[] opaque;

            public Offer(@JsonProperty("id") long id, @JsonProperty("sdp") String sdp, @JsonProperty("type") Type type,
                    @JsonProperty("opaque") byte[] opaque) {
                super();
                this.id = id;
                this.sdp = sdp;
                this.type = type;
                this.opaque = opaque;
            }

            static Offer from(OfferMessage offerMessage) {
                return new Offer(offerMessage.getId(), offerMessage.getSdp(), Type.from(offerMessage.getType()),
                        offerMessage.getOpaque());
            }

            public enum Type {
                AUDIO_CALL,
                VIDEO_CALL;

                static Type from(OfferMessage.Type type) {
                    switch (type) {
                        case AUDIO_CALL:
                            return AUDIO_CALL;
                        case VIDEO_CALL:
                        default:
                            return VIDEO_CALL;
                    }
                }
            }

            public long id() {
                return id;
            }

            public String sdp() {
                return sdp;
            }

            public Type type() {
                return type;
            }

            public byte[] getOpaque() {
                return opaque;
            }
        }

        public static class Answer {

            private final long id;
            private final String sdp;
            private final byte[] opaque;

            public Answer(@JsonProperty("id") long id, @JsonProperty("sdp") String sdp,
                    @JsonProperty("opaque") byte[] opaque) {
                super();
                this.id = id;
                this.sdp = sdp;
                this.opaque = opaque;
            }

            static Answer from(AnswerMessage answerMessage) {
                return new Answer(answerMessage.getId(), answerMessage.getSdp(), answerMessage.getOpaque());
            }

            public long id() {
                return id;
            }

            public String sdp() {
                return sdp;
            }

            public byte[] getOpaque() {
                return opaque;
            }
        }

        public static class Busy {
            private final long id;

            public Busy(@JsonProperty("id") long id) {
                super();
                this.id = id;
            }

            static Busy from(BusyMessage busyMessage) {
                return new Busy(busyMessage.getId());
            }

            public long id() {
                return id;
            }
        }

        public static class Hangup {
            private final long id;
            private final Type type;
            private final int deviceId;
            private final boolean isLegacy;

            public Hangup(@JsonProperty("id") long id, @JsonProperty("type") Type type,
                    @JsonProperty("deviceId") int deviceId, @JsonProperty("isLegacy") boolean isLegacy) {
                super();
                this.id = id;
                this.type = type;
                this.deviceId = deviceId;
                this.isLegacy = isLegacy;
            }

            static Hangup from(HangupMessage hangupMessage) {
                return new Hangup(hangupMessage.getId(), Type.from(hangupMessage.getType()),
                        hangupMessage.getDeviceId(), hangupMessage.isLegacy());
            }

            public enum Type {
                NORMAL,
                ACCEPTED,
                DECLINED,
                BUSY,
                NEED_PERMISSION;

                static Type from(HangupMessage.Type type) {
                    switch (type) {
                        case NORMAL:
                            return NORMAL;
                        case ACCEPTED:
                            return ACCEPTED;
                        case DECLINED:
                            return DECLINED;
                        case BUSY:
                            return BUSY;
                        case NEED_PERMISSION:
                        default:
                            return NEED_PERMISSION;
                    }
                }
            }

            public long id() {
                return id;
            }

            public Type type() {
                return type;
            }

            public int deviceId() {
                return deviceId;
            }

            public boolean isLegacy() {
                return isLegacy;
            }
        }

        public static class IceUpdate {

            private final long id;
            private final String sdp;
            private final byte[] opaque;

            public IceUpdate(@JsonProperty("id") long id, @JsonProperty("sdp") String sdp,
                    @JsonProperty("opaque") byte[] opaque) {
                super();
                this.id = id;
                this.sdp = sdp;
                this.opaque = opaque;
            }

            static IceUpdate from(IceUpdateMessage iceUpdateMessage) {
                return new IceUpdate(iceUpdateMessage.getId(), iceUpdateMessage.getSdp(), iceUpdateMessage.getOpaque());
            }

            public long id() {
                return id;
            }

            public String sdp() {
                return sdp;
            }

            public byte[] getOpaque() {
                return opaque;
            }
        }

        public static class Opaque {
            private final byte[] opaque;
            private final Urgency urgency;

            public Opaque(@JsonProperty("opaque") byte[] opaque, @JsonProperty("urgency") Urgency urgency) {
                super();
                this.opaque = opaque;
                this.urgency = urgency;
            }

            static Opaque from(OpaqueMessage opaqueMessage) {
                return new Opaque(opaqueMessage.getOpaque(), Urgency.from(opaqueMessage.getUrgency()));
            }

            public enum Urgency {
                DROPPABLE,
                HANDLE_IMMEDIATELY;

                static Urgency from(OpaqueMessage.Urgency urgency) {
                    switch (urgency) {
                        case DROPPABLE:
                            return DROPPABLE;
                        case HANDLE_IMMEDIATELY:
                        default:
                            return HANDLE_IMMEDIATELY;
                    }
                }
            }

            public byte[] getOpaque() {
                return opaque;
            }

            public Urgency urgency() {
                return urgency;
            }
        }

        public Optional<Integer> destinationDeviceId() {
            return destinationDeviceId;
        }

        public Optional<GroupId> groupId() {
            return groupId;
        }

        public Optional<Long> timestamp() {
            return timestamp;
        }

        public Optional<Offer> offer() {
            return offer;
        }

        public Optional<Answer> answer() {
            return answer;
        }

        public Optional<Hangup> hangup() {
            return hangup;
        }

        public Optional<Busy> busy() {
            return busy;
        }

        public List<IceUpdate> iceUpdate() {
            return iceUpdate;
        }

        public Optional<Opaque> opaque() {
            return opaque;
        }
    }

    public static class Story {
        private final boolean allowsReplies;
        private final Optional<GroupId> groupId;
        private final Optional<Data.Attachment> fileAttachment;
        private final Optional<TextAttachment> textAttachment;

        public Story(@JsonProperty("allowsReplies") boolean allowsReplies,
                @JsonProperty("groupId") Optional<GroupId> groupId,
                @JsonProperty("fileAttachment") Optional<Attachment> fileAttachment,
                @JsonProperty("textAttachment") Optional<TextAttachment> textAttachment) {
            super();
            this.allowsReplies = allowsReplies;
            this.groupId = groupId;
            this.fileAttachment = fileAttachment;
            this.textAttachment = textAttachment;
        }

        public static Story from(SignalServiceStoryMessage storyMessage, final AttachmentFileProvider fileProvider) {
            return new Story(storyMessage.getAllowsReplies().orElse(false),
                    storyMessage.getGroupContext().map(c -> GroupUtils.getGroupIdV2(c.getMasterKey())),
                    storyMessage.getFileAttachment().map(f -> Data.Attachment.from(f, fileProvider)),
                    storyMessage.getTextAttachment().map(t -> TextAttachment.from(t, fileProvider)));
        }

        public static class TextAttachment {
            private final Optional<String> text;
            private final Optional<Style> style;
            private final Optional<Color> textForegroundColor;
            private final Optional<Color> textBackgroundColor;
            private final Optional<Data.Preview> preview;
            private final Optional<Gradient> backgroundGradient;
            private final Optional<Color> backgroundColor;

            public TextAttachment(@JsonProperty("text") Optional<String> text,
                    @JsonProperty("style") Optional<Style> style,
                    @JsonProperty("textForegroundColor") Optional<Color> textForegroundColor,
                    @JsonProperty("textBackgroundColor") Optional<Color> textBackgroundColor,
                    @JsonProperty("preview") Optional<Data.Preview> preview,
                    @JsonProperty("backgroundGradient") Optional<Gradient> backgroundGradient,
                    @JsonProperty("backgroundColor") Optional<Color> backgroundColor) {
                super();
                this.text = text;
                this.style = style;
                this.textForegroundColor = textForegroundColor;
                this.textBackgroundColor = textBackgroundColor;
                this.preview = preview;
                this.backgroundGradient = backgroundGradient;
                this.backgroundColor = backgroundColor;
            }

            static TextAttachment from(SignalServiceTextAttachment textAttachment,
                    final AttachmentFileProvider fileProvider) {
                return new TextAttachment(textAttachment.getText(), textAttachment.getStyle().map(Style::from),
                        textAttachment.getTextForegroundColor().map(Color::new),
                        textAttachment.getTextBackgroundColor().map(Color::new),
                        textAttachment.getPreview().map(p -> Data.Preview.from(p, fileProvider)),
                        textAttachment.getBackgroundGradient().map(Gradient::from),
                        textAttachment.getBackgroundColor().map(Color::new));
            }

            public enum Style {
                DEFAULT,
                REGULAR,
                BOLD,
                SERIF,
                SCRIPT,
                CONDENSED;

                static Style from(SignalServiceTextAttachment.Style style) {
                    switch (style) {
                        case DEFAULT:
                            return DEFAULT;
                        case REGULAR:
                            return REGULAR;
                        case BOLD:
                            return BOLD;
                        case SERIF:
                            return SERIF;
                        case SCRIPT:
                            return SCRIPT;
                        case CONDENSED:
                        default:
                            return CONDENSED;
                    }
                }
            }

            public static class Gradient {

                private final List<Color> colors;
                private final List<Float> positions;
                private final Optional<Integer> angle;

                public Gradient(@JsonProperty("colors") List<Color> colors,
                        @JsonProperty("positions") List<Float> positions,
                        @JsonProperty("angle") Optional<Integer> angle) {
                    super();
                    this.colors = colors;
                    this.positions = positions;
                    this.angle = angle;
                }

                static Gradient from(SignalServiceTextAttachment.Gradient gradient) {
                    return new Gradient(gradient.getColors().stream().map(Color::new).collect(Collectors.toList()),
                            gradient.getPositions(), gradient.getAngle());
                }

                public List<Color> colors() {
                    return colors;
                }

                public List<Float> positions() {
                    return positions;
                }

                public Optional<Integer> angle() {
                    return angle;
                }
            }

            public Optional<String> text() {
                return text;
            }

            public Optional<Style> style() {
                return style;
            }

            public Optional<Color> textForegroundColor() {
                return textForegroundColor;
            }

            public Optional<Color> textBackgroundColor() {
                return textBackgroundColor;
            }

            public Optional<Data.Preview> getPreview() {
                return preview;
            }

            public Optional<Gradient> backgroundGradient() {
                return backgroundGradient;
            }

            public Optional<Color> backgroundColor() {
                return backgroundColor;
            }
        }

        public boolean isAllowsReplies() {
            return allowsReplies;
        }

        public Optional<GroupId> getGroupId() {
            return groupId;
        }

        public Optional<Data.Attachment> getFileAttachment() {
            return fileAttachment;
        }

        public Optional<TextAttachment> getTextAttachment() {
            return textAttachment;
        }
    }

    public static MessageEnvelope from(SignalServiceEnvelope envelope, SignalServiceContent content,
            RecipientResolver recipientResolver, RecipientAddressResolver addressResolver,
            final AttachmentFileProvider fileProvider, Exception exception) {
        final var source = !envelope.isUnidentifiedSender() && envelope.hasSourceUuid()
                ? recipientResolver.resolveRecipient(envelope.getSourceAddress())
                : envelope.isUnidentifiedSender() && content != null
                        ? recipientResolver.resolveRecipient(content.getSender())
                        : exception instanceof ProtocolException
                                ? recipientResolver.resolveRecipient(((ProtocolException) exception).getSender())
                                : null;
        final var sourceDevice = envelope.hasSourceDevice() ? envelope.getSourceDevice()
                : content != null ? content.getSenderDevice()
                        : exception instanceof ProtocolException ? ((ProtocolException) exception).getSenderDevice()
                                : 0;

        Optional<Receipt> receipt;
        Optional<Typing> typing;
        Optional<Data> data;
        Optional<Sync> sync;
        Optional<Call> call;
        Optional<Story> story;
        if (content != null) {
            receipt = content.getReceiptMessage().map(Receipt::from);
            typing = content.getTypingMessage().map(Typing::from);
            data = content.getDataMessage()
                    .map(dataMessage -> Data.from(dataMessage, recipientResolver, addressResolver, fileProvider));
            sync = content.getSyncMessage().map(s -> Sync.from(s, recipientResolver, addressResolver, fileProvider));
            call = content.getCallMessage().map(Call::from);
            story = content.getStoryMessage().map(s -> Story.from(s, fileProvider));
        } else {
            receipt = envelope.isReceipt() ? Optional.of(new Receipt(envelope.getServerReceivedTimestamp(),
                    Receipt.Type.DELIVERY, List.of(envelope.getTimestamp()))) : Optional.empty();
            typing = Optional.empty();
            data = Optional.empty();
            sync = Optional.empty();
            call = Optional.empty();
            story = Optional.empty();
        }

        return new MessageEnvelope(
                source == null ? Optional.empty()
                        : Optional.of(addressResolver.resolveRecipientAddress(source).toApiRecipientAddress()),
                sourceDevice, envelope.getTimestamp(), envelope.getServerReceivedTimestamp(),
                envelope.getServerDeliveredTimestamp(), envelope.isUnidentifiedSender(), receipt, typing, data, sync,
                call, story);
    }

    public interface AttachmentFileProvider {

        File getFile(SignalServiceAttachmentPointer pointer);
    }

    public Optional<RecipientAddress> getSourceAddress() {
        return sourceAddress;
    }

    public int getSourceDevice() {
        return sourceDevice;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getServerReceivedTimestamp() {
        return serverReceivedTimestamp;
    }

    public long getServerDeliveredTimestamp() {
        return serverDeliveredTimestamp;
    }

    public boolean isUnidentifiedSender() {
        return isUnidentifiedSender;
    }

    public Optional<Receipt> getReceipt() {
        return receipt;
    }

    public Optional<Typing> getTyping() {
        return typing;
    }

    public Optional<Data> getData() {
        return data;
    }

    public Optional<Sync> getSync() {
        return sync;
    }

    public Optional<Call> getCall() {
        return call;
    }

    public Optional<Story> getStory() {
        return story;
    }
}
