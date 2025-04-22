package org.openhab.binding.wyoming.internal.protocol.message;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

public class MessageTypeAdapter implements JsonSerializer<MessageType>, JsonDeserializer<MessageType> {

    @Override
    public JsonElement serialize(MessageType src, Type typeOfSrc, JsonSerializationContext context) {
        // Serialize using the stringRepresentation of the enum
        return new JsonPrimitive(src.stringRepresentation);
    }

    @Override
    public MessageType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        // Deserialize back to a MessageType using the stringRepresentation
        return MessageType.fromString(json.getAsString());
    }
}
