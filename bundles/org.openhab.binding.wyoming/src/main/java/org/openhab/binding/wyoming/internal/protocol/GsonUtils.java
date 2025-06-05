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
package org.openhab.binding.wyoming.internal.protocol;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.wyoming.internal.protocol.message.MessageType;
import org.openhab.binding.wyoming.internal.protocol.message.MessageTypeAdapter;
import org.openhab.binding.wyoming.internal.protocol.message.data.WyomingData;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;

/**
 * Utility for gson parsing
 *
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class GsonUtils {

    private static final Gson gson = new GsonBuilder().registerTypeAdapter(MessageType.class, new MessageTypeAdapter())
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    private static final Type type = new TypeToken<Map<String, Object>>() {
    }.getType();

    public static String toJson(Object object) {
        return gson.toJson(object);
    }

    public static @Nullable Map<String, Object> fromJsonToMap(String json) throws WyomingProtocolException {
        try {
            return gson.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            WyomingProtocolException wyomingProtocolException = new WyomingProtocolException("Cannot parse JSON", e);
            wyomingProtocolException.setJson(json);
            throw wyomingProtocolException;
        }
    }

    public static <T> T fromJsonToMap(String json, Class<T> clazz) throws WyomingProtocolException {
        try {
            return gson.fromJson(json, clazz);
        } catch (JsonSyntaxException e) {
            WyomingProtocolException wyomingProtocolException = new WyomingProtocolException(
                    "Cannot parse JSON to " + clazz.getName(), e);
            wyomingProtocolException.setJson(json);
            throw wyomingProtocolException;
        }
    }

    public static <T> T fromJsonToMap(JsonElement json, Class<T> clazz) {
        return gson.fromJson(json, clazz);
    }

    public static <T> T convertMapTo(Map<String, Object> additionalData, Class<T> dataClass) {
        JsonElement jsonElement = gson.toJsonTree(additionalData);
        return fromJsonToMap(jsonElement, dataClass);
    }

    public static @Nullable HashMap<String, Object> convertDataToMap(WyomingData additionalData) {
        JsonElement jsonElement = gson.toJsonTree(additionalData);
        return gson.fromJson(jsonElement, new TypeToken<HashMap<String, Object>>() {
        }.getType());
    }
}
