package org.openhab.binding.wyoming.internal.protocol.message;

import org.openhab.binding.wyoming.internal.protocol.GsonUtils;
import org.openhab.binding.wyoming.internal.protocol.WyomingClient;
import org.openhab.binding.wyoming.internal.protocol.message.data.WyomingData;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class WyomingRawMessage {

    public static final Charset ENCODING = WyomingClient.ENCODING;
    byte[] newLineBytes = "\n".getBytes(ENCODING);

    private final WyomingMessageHeader header;
    private Map<String, Object> additionalData = null;
    private byte[] payload = null;

    public WyomingRawMessage(MessageType messageType) {
        this(new WyomingMessageHeader(messageType));
    }

    public WyomingRawMessage(WyomingMessageHeader header) {
        this(header, null);
    }

    public WyomingRawMessage(WyomingMessageHeader header, WyomingData additionalData) {
        this(header, additionalData, null);
    }

    public WyomingRawMessage(WyomingMessageHeader header, WyomingData additionalData, byte[] payload) {
        this.header = header;
        setAdditionalData(additionalData);
        this.payload = payload;
    }

    public Map<String, Object> getAdditionalData() {
        return additionalData;
    }

    public byte[] getPayload() {
        return payload;
    }

    public Map<String, Object> getMergedData() {
        Map<String, Object> mergedData = new HashMap<>();
        Map<String, Object> additionalDataLocal = additionalData;
        if (additionalDataLocal != null && !additionalDataLocal.isEmpty()) {
            mergedData.putAll(additionalDataLocal);
        }
        Map<String, Object> localData = header.data;
        if (localData != null && !localData.isEmpty()) {
            mergedData.putAll(localData);
        }
        if (!mergedData.isEmpty()) {
            return mergedData;
        } else {
            return null;
        }
    }

    public void setAdditionalData(Map<String, Object> additionalData) {
        this.additionalData = additionalData;
    }

    public void setAdditionalData(WyomingData messageData) {
        if (messageData != null) {
            this.additionalData = GsonUtils.convertDataToMap(messageData);
        }
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public WyomingMessageHeader getHeader() {
        return header;
    }

    @Override
    public String toString() {
        return new String(toBytes(), ENCODING);
    }

    public byte[] toBytes() {
        int messageLength = newLineBytes.length;
        int position = 0;
        String jsonAdditionalData = null;
        if (additionalData != null && !additionalData.isEmpty()) {
            jsonAdditionalData = GsonUtils.toJson(additionalData);
            header.setDataLength(jsonAdditionalData.length());
            messageLength += jsonAdditionalData.length();
        }
        if (payload != null) {
            header.setPayloadLength(payload.length);
            messageLength += payload.length;
        }
        String headerJson = header.toJson();
        messageLength += headerJson.length();
        byte[] messageBytes = new byte[messageLength];
        byte[] headerBytes = headerJson.getBytes(ENCODING);
        System.arraycopy(headerBytes, 0, messageBytes, 0, headerBytes.length);
        position = headerBytes.length;
        System.arraycopy(newLineBytes, 0, messageBytes, position, newLineBytes.length);
        position += newLineBytes.length;
        if (jsonAdditionalData != null) {
            System.arraycopy(jsonAdditionalData.getBytes(ENCODING), 0, messageBytes, position, jsonAdditionalData.length());
            position += jsonAdditionalData.length();
        }
        if (payload != null) {
            System.arraycopy(payload, 0, messageBytes, position, payload.length);
        }
        return messageBytes;
    }

    public <T extends WyomingData> T decodeData(Class<T> dataClass) {
        WyomingData wyomingData = GsonUtils.convertMapTo(getMergedData(), getHeader().getType().getRelatedClass());
        if (dataClass.isInstance(wyomingData)) {
            return (T) wyomingData;
        } else {
            throw new IllegalArgumentException("Cannot decode data to " + dataClass.getName());
        }
    }
}
