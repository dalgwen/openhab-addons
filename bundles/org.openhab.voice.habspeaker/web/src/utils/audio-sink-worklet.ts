import { AudioCache } from "./audio-cache";
/**
 * The {@link AudioSinkWorklet} is the {@link AudioWorkletProcessor} implementation used by the {@link AudioSink} class.
 * 
 */
export class AudioSinkWorklet extends AudioWorkletProcessor {
    private audioCache = new AudioCache();
    private streaming: boolean = true;
    private done: boolean = false;
    constructor() {
        super();
        this.port.onmessage = (ev) => this.handlePortMessage(ev.data);
    }
    handlePortMessage(data: Float32Array | Float32Array[] | false) {
        if (data instanceof Float32Array) {
            this.audioCache.writeAudioData(data);
        } else if (Array.isArray(data)) {
            data.forEach(buffer => this.audioCache.writeAudioData(buffer));
        } else if (data === false) {
            this.streaming = false;
        }
    }
    process(_: Float32Array[][], outputs: Float32Array[][]) {
        const frameLength = outputs[0][0].length;
        const channels = outputs[0].length;
        const bufferSize = frameLength * channels;
        const dataAvailable = this.audioCache.available(bufferSize);
        if (!dataAvailable) {
            if (this.streaming) {
                return true;
            } else {
                if (this.audioCache.size() !== 0) {
                    // send remaining audio
                    const audioData = new Float32Array(bufferSize);
                    audioData.set(this.audioCache.readAudioData(this.audioCache.size()), 0);
                    this.writeAudioSamples(audioData, outputs, channels);
                    return true;
                }
                if (!this.done) {
                    this.done = true;
                    // notify completion
                    this.port.postMessage(false);
                    this.port.close();
                }
                return false;
            }
        }
        const audioData = this.audioCache.readAudioData(bufferSize);
        this.writeAudioSamples(audioData, outputs, channels);
        return true;
    }

    private writeAudioSamples(audioData: Float32Array, outputs: Float32Array[][], channels: number) {
        if (channels == 1) {
            outputs[0][0].set(audioData, 0);
        } else {
            const channelsData = outputs[0];
            audioData.forEach((sample, sampleNumber) => {
                const channelIndex = sampleNumber % channels;
                channelsData[channelIndex][(sampleNumber - channelIndex) / channels] = sample;
            });
        }
    }
}

registerProcessor("habspeaker-sink-worklet", AudioSinkWorklet);