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

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.wyoming.internal.protocol.GsonUtils;
import org.openhab.binding.wyoming.internal.protocol.WyomingProtocolException;

/**
 * Header for all wyoming messages
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class WyomingMessageHeader {

    public static final String TYPE = "type";
    public static final String DATA = "data";
    public static final String DATA_LENGTH = "data_length";
    public static final String PAYLOAD_LENGTH = "payload_length";

    MessageType type;
    @Nullable
    Map<String, Object> data = null;
    @Nullable
    Integer dataLength = null;
    @Nullable
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

    @Nullable
    public Map<String, Object> getData() {
        return data;
    }

    public WyomingMessageHeader setData(Map<String, Object> data) {
        this.data = data;
        return this;
    }

    @Nullable
    public Integer getDataLength() {
        return dataLength;
    }

    protected void setDataLength(Integer dataLength) {
        this.dataLength = dataLength;
    }

    @Nullable
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
