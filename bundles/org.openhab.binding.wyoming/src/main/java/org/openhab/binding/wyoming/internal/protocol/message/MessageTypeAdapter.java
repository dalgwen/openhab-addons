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

import java.lang.reflect.Type;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * Automatic adapter for MessageType json exchanged
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class MessageTypeAdapter implements JsonSerializer<MessageType>, JsonDeserializer<MessageType> {

    @Override
    public JsonElement serialize(MessageType src, Type typeOfSrc, JsonSerializationContext context) {
        // Serialize using the stringRepresentation of the enum
        return new JsonPrimitive(src.stringRepresentation);
    }

    @Override
    @Nullable
    public MessageType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        // Deserialize back to a MessageType using the stringRepresentation
        return MessageType.fromString(json.getAsString());
    }
}
