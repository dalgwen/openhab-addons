package org.openhab.binding.wyoming.internal.protocol.message.data;

import java.util.List;

public class InfoData extends WyomingData {

    private List<Asr> asr;
    private List<Tts> tts;
    private List<Wake> wake;
    private List<Handle> handle;
    private List<Intent> intent;
    private Satellite satellite;
    private List<Mic> mic;
    private List<Snd> snd;

    public List<Asr> getAsr() {
        return asr;
    }

    public void setAsr(List<Asr> asr) {
        this.asr = asr;
    }

    public List<Tts> getTts() {
        return tts;
    }

    public void setTts(List<Tts> tts) {
        this.tts = tts;
    }

    public List<Wake> getWake() {
        return wake;
    }

    public void setWake(List<Wake> wake) {
        this.wake = wake;
    }

    public List<Handle> getHandle() {
        return handle;
    }

    public void setHandle(List<Handle> handle) {
        this.handle = handle;
    }

    public List<Intent> getIntent() {
        return intent;
    }

    public void setIntent(List<Intent> intent) {
        this.intent = intent;
    }

    public Satellite getSatellite() {
        return satellite;
    }

    public void setSatellite(Satellite satellite) {
        this.satellite = satellite;
    }

    public List<Mic> getMic() {
        return mic;
    }

    public void setMic(List<Mic> mic) {
        this.mic = mic;
    }

    public List<Snd> getSnd() {
        return snd;
    }

    public void setSnd(List<Snd> snd) {
        this.snd = snd;
    }

    @Override
    public String toString() {
        return "InfoData{" +
                "asr=" + asr +
                ", tts=" + tts +
                ", wake=" + wake +
                ", handle=" + handle +
                ", intent=" + intent +
                ", satellite=" + satellite +
                ", mic=" + mic +
                ", snd=" + snd +
                '}';
    }

    public static class Asr {
        private List<Model> models;

        public List<Model> getModels() {
            return models;
        }

        public void setModels(List<Model> models) {
            this.models = models;
        }

        @Override
        public String toString() {
            return "Asr{" +
                    "models=" + models +
                    '}';
        }
    }

    public static class Tts {
        private List<TtsModel> models;

        public List<TtsModel> getModels() {
            return models;
        }

        public void setModels(List<TtsModel> models) {
            this.models = models;
        }

        @Override
        public String toString() {
            return "Tts{" +
                    "models=" + models +
                    '}';
        }
    }

    public static class Wake {
        private List<Model> models;

        public List<Model> getModels() {
            return models;
        }

        public void setModels(List<Model> models) {
            this.models = models;
        }
    }

    public static class Handle {
        private List<Model> models;

        public List<Model> getModels() {
            return models;
        }

        public void setModels(List<Model> models) {
            this.models = models;
        }

        @Override
        public String toString() {
            return "Handle{" +
                    "models=" + models +
                    '}';
        }
    }

    public static class Intent {
        private List<Model> models;

        public List<Model> getModels() {
            return models;
        }

        public void setModels(List<Model> models) {
            this.models = models;
        }

        @Override
        public String toString() {
            return "Intent{" +
                    "models=" + models +
                    '}';
        }
    }

    public static class Satellite {
        private String name;
        private String area;
        private Boolean hasVad;
        private List<String> activeWakeWords;
        private Integer maxActiveWakeWords;
        private Boolean supportsTrigger;

        public String getArea() {
            return area;
        }

        public void setArea(String area) {
            this.area = area;
        }

        public Boolean getHasVad() {
            return hasVad;
        }

        public void setHasVad(Boolean hasVad) {
            this.hasVad = hasVad;
        }

        public List<String> getActiveWakeWords() {
            return activeWakeWords;
        }

        public void setActiveWakeWords(List<String> activeWakeWords) {
            this.activeWakeWords = activeWakeWords;
        }

        public Integer getMaxActiveWakeWords() {
            return maxActiveWakeWords;
        }

        public void setMaxActiveWakeWords(Integer maxActiveWakeWords) {
            this.maxActiveWakeWords = maxActiveWakeWords;
        }

        public Boolean getSupportsTrigger() {
            return supportsTrigger;
        }

        public void setSupportsTrigger(Boolean supportsTrigger) {
            this.supportsTrigger = supportsTrigger;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "Satellite{" +
                    "name='" + name + '\'' +
                    ", area='" + area + '\'' +
                    ", hasVad=" + hasVad +
                    ", activeWakeWords=" + activeWakeWords +
                    ", maxActiveWakeWords=" + maxActiveWakeWords +
                    ", supportsTrigger=" + supportsTrigger +
                    '}';
        }
    }

    public static class Mic {
        private MicFormat micFormat;

        public MicFormat getMicFormat() {
            return micFormat;
        }

        public void setMicFormat(MicFormat micFormat) {
            this.micFormat = micFormat;
        }

        @Override
        public String toString() {
            return "Mic{" +
                    "micFormat=" + micFormat +
                    '}';
        }

        public static class MicFormat {
            private Integer rate;
            private Integer width;
            private Integer channels;

            @Override
            public String toString() {
                return "MicFormat{" +
                        "rate=" + rate +
                        ", width=" + width +
                        ", channels=" + channels +
                        '}';
            }
        }
    }

    public static class Snd {
        private SndFormat sndFormat;

        public SndFormat getSndFormat() {
            return sndFormat;
        }

        public void setSndFormat(SndFormat sndFormat) {
            this.sndFormat = sndFormat;
        }

        @Override
        public String toString() {
            return "Snd{" +
                    "sndFormat=" + sndFormat +
                    '}';
        }

        public static class SndFormat {
            private Integer rate;
            private Integer width;
            private Integer channels;

            @Override
            public String toString() {
                return "SndFormat{" +
                        "rate=" + rate +
                        ", width=" + width +
                        ", channels=" + channels +
                        '}';
            }
        }
    }

    public static class Model {
        private String name;
        private List<String> languages;
        private Attribution attribution;
        private Boolean installed;
        private String description;
        private String version;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getLanguages() {
            return languages;
        }

        public void setLanguages(List<String> languages) {
            this.languages = languages;
        }

        public Attribution getAttribution() {
            return attribution;
        }

        public void setAttribution(Attribution attribution) {
            this.attribution = attribution;
        }

        public Boolean getInstalled() {
            return installed;
        }

        public void setInstalled(Boolean installed) {
            this.installed = installed;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        @Override
        public String toString() {
            return "Model{" +
                    "name='" + name + '\'' +
                    ", languages=" + languages +
                    ", attribution=" + attribution +
                    ", installed=" + installed +
                    ", description='" + description + '\'' +
                    ", version='" + version + '\'' +
                    '}';
        }

        public static class Attribution {
            private String name;
            private String url;

            @Override
            public String toString() {
                return "Attribution{" +
                        "name='" + name + '\'' +
                        ", url='" + url + '\'' +
                        '}';
            }
        }
    }

    public static class TtsModel extends Model {
        private List<Speaker> speakers;

        public List<Speaker> getSpeakers() {
            return speakers;
        }

        public void setSpeakers(List<Speaker> speakers) {
            this.speakers = speakers;
        }

        @Override
        public String toString() {
            return "TtsModel{" +
                    "speakers=" + speakers +
                    '}';
        }

        public static class Speaker {
            private String name;

            @Override
            public String toString() {
                return "Speaker{" +
                        "name='" + name + '\'' +
                        '}';
            }
        }
    }
}