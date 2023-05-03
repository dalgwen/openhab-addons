import { AudioCache } from "./audio-cache";
/**
 * The {@link AudioSinkWorklet} is the {@link AudioWorkletProcessor} implementation used by the {@link AudioSink} class.
 * 
 */
export class AudioSinkWorklet extends AudioWorkletProcessor {
    playing: boolean = false;
    audioCache = new AudioCache();
    constructor() {
        super();
        this.port.onmessage = (ev) => {
            const type = ev.data.type;
            const port = ev.data.port;
            if (type === 'audio_input_port' && port instanceof MessagePort) {
                // this port is directly connected to the io webworker
                port.onmessage = (ev) => {
                    if (ev.data instanceof Float32Array) {
                        this.audioCache.writeAudioData(ev.data);
                    } else if (Array.isArray(ev.data)) {
                        ev.data.forEach(buffer => this.audioCache.writeAudioData(buffer));
                    }
                };
                this.port.postMessage({ type: 'ready' });
            }
        };
    }
    listener(listening: boolean) {
        this.port.postMessage({ type: 'listening', value: listening });
    }
    process(inputs: Float32Array[][], outputs: Float32Array[][]) {
        const channels = outputs[0].length;
        const frameLength = outputs[0][0].length;
        const bufferSize = frameLength * channels;
        if (!this.audioCache.available(bufferSize)) {
            if (this.playing) {
                this.playing = false;
                this.listener(false);
            }
            return true;
        }
        if (!this.playing) {
            this.playing = true;
            this.listener(true);
        }
        const audioData = this.audioCache.readAudioData(bufferSize);
        if (channels == 1) {
            outputs[0][0].set(audioData, 0);
        } else {
            const channelsData = outputs[0];
            audioData.forEach((sample, sampleNumber) => {
                const channelIndex = sampleNumber % channels;
                channelsData[channelIndex][(sampleNumber - channelIndex) / channels] = sample;
            });
        }
        return true;
    }
}

registerProcessor("habspeaker-sink-worklet", AudioSinkWorklet);