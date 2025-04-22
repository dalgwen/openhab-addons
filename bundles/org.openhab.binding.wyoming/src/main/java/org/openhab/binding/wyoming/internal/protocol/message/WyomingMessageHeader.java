package org.openhab.binding.wyoming.internal.protocol.message;

import org.openhab.binding.wyoming.internal.protocol.GsonUtils;
import org.openhab.binding.wyoming.internal.protocol.WyomingProtocolException;

import java.util.Map;

public class WyomingMessageHeader {

    public static final String TYPE = "type";
    public static final String DATA = "data";
    public static final String DATA_LENGTH = "data_length";
    public static final String PAYLOAD_LENGTH = "payload_length";

    MessageType type;
    Map<String, Object> data = null;
    Integer dataLength = null;
    Integer payloadLength = null;

    public WyomingMessageHeader(MessageType type) {
        this.type = type;
    }

    public static WyomingMessageHeader from(String line) throws WyomingProtocolException {
        return GsonUtils.fromJsonToMap(line, WyomingMessageHeader.class);
    }

    public MessageType getType() {
        return type;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public WyomingMessageHeader setData(Map<String, Object> data) {
        this.data = data;
        return this;
    }

    public Integer getDataLength() {
        return dataLength;
    }

    protected void setDataLength(Integer dataLength) {
        this.dataLength = dataLength;
    }

    public Integer getPayloadLength() {
        return payloadLength;
    }

    protected void setPayloadLength(Integer payloadLength) {
        this.payloadLength = payloadLength;
    }

    public String toJson() {
        return GsonUtils.toJson(this);
    }
}
