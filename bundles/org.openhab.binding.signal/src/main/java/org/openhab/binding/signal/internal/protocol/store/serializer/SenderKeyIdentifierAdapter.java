/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
package org.openhab.binding.signal.internal.protocol.store.serializer;

import java.lang.reflect.Type;
import java.util.Base64;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.signal.internal.protocol.store.SenderKeyIdentifier;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * Serialize/deserialize sender key
 *
 * @author Gwendal ROULLEAU - Initial contribution
 */
@NonNullByDefault
public class SenderKeyIdentifierAdapter
        implements JsonDeserializer<SenderKeyIdentifier>, JsonSerializer<SenderKeyIdentifier> {

    @Override
    public JsonElement serialize(SenderKeyIdentifier src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(Base64.getEncoder().encodeToString(src.serialize()));
    }

    @Override
    public @Nullable SenderKeyIdentifier deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        return SenderKeyIdentifier.deserialize(Base64.getDecoder().decode(json.getAsString()));
    }
}
