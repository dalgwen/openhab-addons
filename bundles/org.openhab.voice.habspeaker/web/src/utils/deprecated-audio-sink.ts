import { AudioNodeSink } from "./audio-node-sink";
import { AudioCache } from "./audio-cache";
const AUDIO_BUFFER_SIZE = 4096;
/**
 * The {@link DeprecatedAudioSink} feeds the audio data send by a MessagePort to the audio system using a {@link ScriptProcessorNode} instance 
 */
export class DeprecatedAudioSink extends AudioNodeSink {
    private audioCache: AudioCache;
    private port: MessagePort;
    private externalPort: MessagePort;
    private silence = new Float32Array(AUDIO_BUFFER_SIZE);
    private streaming: boolean = true;
    private done: boolean = false;
    constructor(id: string, audioContext: AudioContext, channels: number, volume: number) {
        const sinkProcessorNode = audioContext.createScriptProcessor(AUDIO_BUFFER_SIZE, 0, channels);
        super(id, audioContext, sinkProcessorNode, channels, volume);
        this.audioCache = new AudioCache();
        sinkProcessorNode.onaudioprocess = this.processAudio.bind(this);
        const channel = new MessageChannel();
        this.port = channel.port1;
        this.port.onmessage = (ev) => this.handlePortMessage(ev.data);
        this.externalPort = channel.port2;
    }
    async getPort() {
        return this.externalPort;
    }
    private handlePortMessage(data: Float32Array | Float32Array[] | false) {
        if (data instanceof Float32Array) {
            this.audioCache.writeAudioData(data);
        } else if (Array.isArray(data)) {
            data.forEach(buffer => this.audioCache.writeAudioData(buffer));
        } else if (data === false) {
            this.streaming = false;
        }
    }
    private processAudio(e: AudioProcessingEvent) {
        const dataAvailable = this.audioCache.available(AUDIO_BUFFER_SIZE);
        if (!dataAvailable) {
            if (this.streaming) {
                for (let c = 0; c < this.channels; c++) {
                    e.outputBuffer.getChannelData(c).set(this.silence);
                }
                return true;
            } else {
                if (this.audioCache.size() !== 0) {
                    // send remaining audio
                    const audioData = new Float32Array(AUDIO_BUFFER_SIZE);
                    audioData.set(this.audioCache.readAudioData(this.audioCache.size()), 0);
                    this.writeAudioSamples(audioData, e);
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
        const audioData = this.audioCache.readAudioData(AUDIO_BUFFER_SIZE);
        this.writeAudioSamples(audioData, e);
        return true;
    }
    private writeAudioSamples(audioData: Float32Array, e: AudioProcessingEvent) {
        const channelsData: Float32Array[] = [];
        if (this.channels == 1) {
            channelsData[0] = audioData;
        } else {
            const length = audioData.byteLength / audioData.BYTES_PER_ELEMENT;
            for (let s = 0; s < length; s++) {
                // the channel index
                const c = s % this.channels;
                // the index inside the buffer channel
                const i = (s - c) / this.channels;
                const channelData = channelsData[c] = (channelsData[c] ?? new Float32Array(length / this.channels));
                channelData[i] = audioData[s];
            }
        }
        for (let c = 0; c < this.channels; c++) {
            e.outputBuffer
                .getChannelData(c)
                .set(channelsData[c]);
        }
    }
}
