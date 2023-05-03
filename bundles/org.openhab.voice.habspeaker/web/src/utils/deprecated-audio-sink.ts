import { AudioNodeSink } from "./audio-node-sink";
import { AudioCache } from "./audio-cache";
const AUDIO_BUFFER_SIZE = 4096;
/**
 * The {@link DeprecatedAudioSink} feeds the audio data send by a MessagePort to the audio system using a {@link ScriptProcessorNode} instance 
 */
export class DeprecatedAudioSink extends AudioNodeSink {
    private audioCache: AudioCache;
    private silence = new Float32Array(AUDIO_BUFFER_SIZE);
    constructor(id: string, audioContext: AudioContext, channels: number, volume: number, listener: (playing: boolean) => void) {
        const sinkProcessorNode = audioContext.createScriptProcessor(AUDIO_BUFFER_SIZE, 0, channels);
        super(id, audioContext, sinkProcessorNode, channels, volume, listener);
        this.audioCache = new AudioCache();
        sinkProcessorNode.onaudioprocess = this.processAudio.bind(this);
    }
    async setPort(port: MessagePort) {
        port.onmessage = ev => {
            if (ev.data instanceof Float32Array) {
                this.audioCache.writeAudioData(ev.data);
            }
        };
    }
    private processAudio(e: AudioProcessingEvent) {
        if (this.audioCache.available(AUDIO_BUFFER_SIZE)) {
            if (!this.playing) {
                this.playing = true;
                this.listener(true);
            }
            const audioData = this.audioCache.readAudioData(e.outputBuffer.length * this.channels);
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
        } else {
            if (this.playing) {
                this.playing = false;
                this.listener(false);
            }
            for (let c = 0; c < this.channels; c++) {
                e.outputBuffer.getChannelData(c).set(this.silence);
            }
        }
    };
}
