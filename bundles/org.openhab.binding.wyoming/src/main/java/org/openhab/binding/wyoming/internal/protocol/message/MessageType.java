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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.wyoming.internal.protocol.message.data.AudioChunkData;
import org.openhab.binding.wyoming.internal.protocol.message.data.AudioStartData;
import org.openhab.binding.wyoming.internal.protocol.message.data.AudioStopData;
import org.openhab.binding.wyoming.internal.protocol.message.data.DescribeData;
import org.openhab.binding.wyoming.internal.protocol.message.data.InfoData;
import org.openhab.binding.wyoming.internal.protocol.message.data.NotImplementedData;
import org.openhab.binding.wyoming.internal.protocol.message.data.RunPipelineData;
import org.openhab.binding.wyoming.internal.protocol.message.data.RunSatelliteData;
import org.openhab.binding.wyoming.internal.protocol.message.data.WyomingData;

/**
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public enum MessageType {

    AudioChunk("audio-chunk", AudioChunkData.class),
    AudioStart("audio-start", AudioStartData.class),
    AudioStop("audio-stop", AudioStopData.class),
    Describe("describe", DescribeData.class),
    Info("info", InfoData.class),
    Transcribe("transcribe", NotImplementedData.class),
    Transcript("transcript", NotImplementedData.class),
    Synthesize("synthesize", NotImplementedData.class),
    Detect("detect", NotImplementedData.class),
    Detection("detection", NotImplementedData.class),
    NotDetected("not-detected", NotImplementedData.class),
    VoiceStarted("voice-started", NotImplementedData.class),
    VoiceStopped("voice-stopped", NotImplementedData.class),
    Recognize("recognize", NotImplementedData.class),
    Intent("intent", NotImplementedData.class),
    NotRecognized("not-recognized", NotImplementedData.class),
    Handled("handled", NotImplementedData.class),
    NotHandled("not-handled", NotImplementedData.class),
    Played("played", NotImplementedData.class),
    RunSatellite("run-satellite", RunSatelliteData.class),
    PauseSatellite("pause-satellite", NotImplementedData.class),
    SatelliteConnected("satellite-connected", NotImplementedData.class),
    SatelliteDisconnected("satellite-disconnected", NotImplementedData.class),
    StreamingStarted("streaming-started", NotImplementedData.class),
    StreamingStopped("streaming-stopped", NotImplementedData.class),
    RunPipeline("run-pipeline", RunPipelineData.class),
    TooMuchPayload("too-much-payload", NotImplementedData.class);

    final String stringRepresentation;
    private final Class<? extends WyomingData> relatedClass;

    MessageType(String stringRepresentation, Class<? extends WyomingData> relatedClass) {
        this.stringRepresentation = stringRepresentation;
        this.relatedClass = relatedClass;
    }

    public Class<? extends WyomingData> getRelatedClass() {
        return relatedClass;
    }

    @Nullable
    public static MessageType fromRelatedClass(Class<? extends WyomingData> relatedClass) {
        for (MessageType type : MessageType.values()) {
            if (type.relatedClass.equals(relatedClass)) {
                return type;
            }
        }
        return null;
    }

    @Nullable
    public static MessageType fromString(String stringRepresentation) {
        for (MessageType type : MessageType.values()) {
            if (type.stringRepresentation.equals(stringRepresentation)) {
                return type;
            }
        }
        return null;
    }
}
