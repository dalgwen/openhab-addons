/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.wyoming.internal.protocol.message;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.wyoming.internal.protocol.GsonUtils;
import org.openhab.binding.wyoming.internal.protocol.WyomingClient;
import org.openhab.binding.wyoming.internal.protocol.message.data.WyomingData;

/**
 * A raw wyoming message structure
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class WyomingRawMessage {

    public static final Charset ENCODING = WyomingClient.ENCODING;
    byte[] newLineBytes = "\n".getBytes(ENCODING);

    private final WyomingMessageHeader header;
    @Nullable
    private Map<String, Object> additionalData = null;
    private byte @Nullable [] payload = null;

    public WyomingRawMessage(MessageType messageType) {
        this(new WyomingMessageHeader(messageType));
    }

    public WyomingRawMessage(WyomingMessageHeader header) {
        this(header, null);
    }

    public WyomingRawMessage(WyomingMessageHeader header, @Nullable WyomingData additionalData) {
        this(header, additionalData, null);
    }

    public WyomingRawMessage(WyomingMessageHeader header, @Nullable WyomingData additionalData,
            byte @Nullable [] payload) {
        this.header = header;
        setAdditionalData(additionalData);
        this.payload = payload;
    }

    @Nullable
    public Map<String, Object> getAdditionalData() {
        return additionalData;
    }

    public byte @Nullable [] getPayload() {
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
            return Collections.emptyMap();
        }
    }

    public void setAdditionalData(@Nullable Map<String, Object> additionalData) {
        this.additionalData = additionalData;
    }

    public void setAdditionalData(@Nullable WyomingData messageData) {
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
        @Nullable
        Map<String, Object> additionalDataLocal = additionalData;
        if (additionalDataLocal != null && !additionalDataLocal.isEmpty()) {
            jsonAdditionalData = GsonUtils.toJson(additionalDataLocal);
            header.setDataLength(jsonAdditionalData.length());
            messageLength += jsonAdditionalData.length();
        }
        byte @Nullable [] payloadLocal = payload;
        if (payloadLocal != null) {
            header.setPayloadLength(payloadLocal.length);
            messageLength += payloadLocal.length;
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
            System.arraycopy(jsonAdditionalData.getBytes(ENCODING), 0, messageBytes, position,
                    jsonAdditionalData.length());
            position += jsonAdditionalData.length();
        }
        if (payloadLocal != null) {
            System.arraycopy(payloadLocal, 0, messageBytes, position, payloadLocal.length);
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
