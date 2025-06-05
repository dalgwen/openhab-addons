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
package org.openhab.binding.wyoming.internal.protocol.message.data;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * @author Gwendal Roulleau - Initial contribution
 */
@NonNullByDefault
public class InfoData extends WyomingData {

    @Nullable
    private List<Asr> asr;
    @Nullable
    private List<Tts> tts;
    @Nullable
    private List<Wake> wake;
    @Nullable
    private List<Handle> handle;
    @Nullable
    private List<Intent> intent;
    @Nullable
    private Satellite satellite;
    @Nullable
    private List<Mic> mic;
    @Nullable
    private List<Snd> snd;

    public @Nullable List<Asr> getAsr() {
        return asr;
    }

    public void setAsr(List<Asr> asr) {
        this.asr = asr;
    }

    public @Nullable List<Tts> getTts() {
        return tts;
    }

    public void setTts(List<Tts> tts) {
        this.tts = tts;
    }

    public @Nullable List<Wake> getWake() {
        return wake;
    }

    public void setWake(List<Wake> wake) {
        this.wake = wake;
    }

    public @Nullable List<Handle> getHandle() {
        return handle;
    }

    public void setHandle(List<Handle> handle) {
        this.handle = handle;
    }

    public @Nullable List<Intent> getIntent() {
        return intent;
    }

    public void setIntent(List<Intent> intent) {
        this.intent = intent;
    }

    public @Nullable Satellite getSatellite() {
        return satellite;
    }

    public void setSatellite(Satellite satellite) {
        this.satellite = satellite;
    }

    public @Nullable List<Mic> getMic() {
        return mic;
    }

    public void setMic(List<Mic> mic) {
        this.mic = mic;
    }

    public @Nullable List<Snd> getSnd() {
        return snd;
    }

    public void setSnd(List<Snd> snd) {
        this.snd = snd;
    }

    @Override
    public String toString() {
        return "InfoData{" + "asr=" + asr + ", tts=" + tts + ", wake=" + wake + ", handle=" + handle + ", intent="
                + intent + ", satellite=" + satellite + ", mic=" + mic + ", snd=" + snd + '}';
    }

    public static class Asr {
        @Nullable
        private List<Model> models;

        @Nullable
        public List<Model> getModels() {
            return models;
        }

        public void setModels(List<Model> models) {
            this.models = models;
        }

        @Override
        public String toString() {
            return "Asr{" + "models=" + models + '}';
        }
    }

    public static class Tts {

        @Nullable
        private List<TtsModel> models;

        @Nullable
        public List<TtsModel> getModels() {
            return models;
        }

        public void setModels(List<TtsModel> models) {
            this.models = models;
        }

        @Override
        public String toString() {
            return "Tts{" + "models=" + models + '}';
        }
    }

    public static class Wake {

        @Nullable
        private List<Model> models;

        @Nullable
        public List<Model> getModels() {
            return models;
        }

        public void setModels(List<Model> models) {
            this.models = models;
        }

        @Override
        public String toString() {
            return "Wake{" + "models=" + models + '}';
        }
    }

    public static class Handle {

        @Nullable
        private List<Model> models;

        @Nullable
        public List<Model> getModels() {
            return models;
        }

        public void setModels(List<Model> models) {
            this.models = models;
        }

        @Override
        public String toString() {
            return "Handle{" + "models=" + models + '}';
        }
    }

    public static class Intent {

        @Nullable
        private List<Model> models;

        @Nullable
        public List<Model> getModels() {
            return models;
        }

        public void setModels(List<Model> models) {
            this.models = models;
        }

        @Override
        public String toString() {
            return "Intent{" + "models=" + models + '}';
        }
    }

    public static class Satellite {

        @Nullable
        private String name;
        @Nullable
        private String area;
        @Nullable
        private Boolean hasVad;
        @Nullable
        private List<String> activeWakeWords;
        @Nullable
        private Integer maxActiveWakeWords;
        @Nullable
        private Boolean supportsTrigger;

        @Nullable
        public String getArea() {
            return area;
        }

        public void setArea(String area) {
            this.area = area;
        }

        @Nullable
        public Boolean getHasVad() {
            return hasVad;
        }

        public void setHasVad(Boolean hasVad) {
            this.hasVad = hasVad;
        }

        @Nullable
        public List<String> getActiveWakeWords() {
            return activeWakeWords;
        }

        public void setActiveWakeWords(List<String> activeWakeWords) {
            this.activeWakeWords = activeWakeWords;
        }

        @Nullable
        public Integer getMaxActiveWakeWords() {
            return maxActiveWakeWords;
        }

        public void setMaxActiveWakeWords(Integer maxActiveWakeWords) {
            this.maxActiveWakeWords = maxActiveWakeWords;
        }

        @Nullable
        public Boolean getSupportsTrigger() {
            return supportsTrigger;
        }

        public void setSupportsTrigger(Boolean supportsTrigger) {
            this.supportsTrigger = supportsTrigger;
        }

        @Nullable
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "Satellite{" + "name='" + name + '\'' + ", area='" + area + '\'' + ", hasVad=" + hasVad
                    + ", activeWakeWords=" + activeWakeWords + ", maxActiveWakeWords=" + maxActiveWakeWords
                    + ", supportsTrigger=" + supportsTrigger + '}';
        }
    }

    public static class Mic {

        @Nullable
        private MicFormat micFormat;

        @Nullable
        public MicFormat getMicFormat() {
            return micFormat;
        }

        public void setMicFormat(MicFormat micFormat) {
            this.micFormat = micFormat;
        }

        @Override
        public String toString() {
            return "Mic{" + "micFormat=" + micFormat + '}';
        }

        public static class MicFormat {
            @Nullable
            private Integer rate;
            @Nullable
            private Integer width;
            @Nullable
            private Integer channels;

            @Override
            public String toString() {
                return "MicFormat{" + "rate=" + rate + ", width=" + width + ", channels=" + channels + '}';
            }
        }
    }

    public static class Snd {
        @Nullable
        private SndFormat sndFormat;

        @Nullable
        public SndFormat getSndFormat() {
            return sndFormat;
        }

        public void setSndFormat(SndFormat sndFormat) {
            this.sndFormat = sndFormat;
        }

        @Override
        public String toString() {
            return "Snd{" + "sndFormat=" + sndFormat + '}';
        }

        public static class SndFormat {
            @Nullable
            private Integer rate;
            @Nullable
            private Integer width;
            @Nullable
            private Integer channels;

            @Override
            public String toString() {
                return "SndFormat{" + "rate=" + rate + ", width=" + width + ", channels=" + channels + '}';
            }
        }
    }

    public static class Model {
        @Nullable
        private String name;
        @Nullable
        private List<String> languages;
        @Nullable
        private Attribution attribution;
        @Nullable
        private Boolean installed;
        @Nullable
        private String description;
        @Nullable
        private String version;

        @Nullable
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Nullable
        public List<String> getLanguages() {
            return languages;
        }

        public void setLanguages(List<String> languages) {
            this.languages = languages;
        }

        @Nullable
        public Attribution getAttribution() {
            return attribution;
        }

        public void setAttribution(Attribution attribution) {
            this.attribution = attribution;
        }

        @Nullable
        public Boolean getInstalled() {
            return installed;
        }

        public void setInstalled(Boolean installed) {
            this.installed = installed;
        }

        @Nullable
        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        @Nullable
        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        @Override
        public String toString() {
            return "Model{" + "name='" + name + '\'' + ", languages=" + languages + ", attribution=" + attribution
                    + ", installed=" + installed + ", description='" + description + '\'' + ", version='" + version
                    + '\'' + '}';
        }

        public static class Attribution {
            @Nullable
            private String name;
            @Nullable
            private String url;

            @Override
            public String toString() {
                return "Attribution{" + "name='" + name + '\'' + ", url='" + url + '\'' + '}';
            }
        }
    }

    public static class TtsModel extends Model {
        @Nullable
        private List<Speaker> speakers;

        @Nullable
        public List<Speaker> getSpeakers() {
            return speakers;
        }

        public void setSpeakers(List<Speaker> speakers) {
            this.speakers = speakers;
        }

        @Override
        public String toString() {
            return "TtsModel{" + "speakers=" + speakers + '}';
        }

        public static class Speaker {
            @Nullable
            private String name;

            @Override
            public String toString() {
                return "Speaker{" + "name='" + name + '\'' + '}';
            }
        }
    }
}
