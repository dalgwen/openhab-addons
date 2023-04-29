const BUFFER_SIZE = 2048;
class AudioCache {
    silence = new Float32Array(BUFFER_SIZE);
    buffer = new Float32Array(0);
    readAudioData(n: number) {
        const segment = this.buffer.slice(0, n);
        this.buffer = this.buffer.slice(n, this.buffer.length);
        return segment;
    }
    writeAudioData(buffer: Float32Array) {
        const currentCacheLength = this.buffer.length;
        const newBuffer = new Float32Array(currentCacheLength + buffer.length);
        newBuffer.set(this.buffer, 0);
        newBuffer.set(buffer, currentCacheLength);
        this.buffer = newBuffer;
    }
    available() {
        return !!this.buffer.length;
    }
    reset() {
        this.buffer = new Float32Array(0);
    }
}
/**
 * Utility class to encapsulate the creation of a media stream destination consumed by an audio element
 * and connected to a deprecated script processor which will forward the audio buffers received though the provided port
 * into the audio system.
 */
export class WebAudioSink {
    public static audioElement?: HTMLAudioElement;
    private static destination?: MediaStreamAudioDestinationNode;
    private gainNode: GainNode;
    private sinkProcessorNode: ScriptProcessorNode;
    private audioCache: AudioCache;
    private playing: boolean = false;
    constructor(private id: string, private audioContext: AudioContext, private channels: number, audioMessagePort: MessagePort, volume: number, private listener: (playing: boolean) => void) {
        if (!WebAudioSink.destination) {
            throw new Error('Sink was not setup');
        }
        this.gainNode = audioContext.createGain();
        this.gainNode.gain.value = (volume / 100);
        this.sinkProcessorNode = audioContext.createScriptProcessor(BUFFER_SIZE, 0, channels);
        this.sinkProcessorNode.onaudioprocess = this.processAudio.bind(this);
        this.sinkProcessorNode.connect(this.gainNode);
        audioMessagePort.onmessage = ev => {
            if (ev.data instanceof Float32Array) {
                this.audioCache.writeAudioData(ev.data);
            }
        };
        this.audioCache = new AudioCache();
        this.gainNode.connect(WebAudioSink.destination);
    }
    getId() {
        return this.id;
    }
    setVolume(value: number) {
        this.gainNode.gain.setValueAtTime((value / 100), this.audioContext.currentTime);
    }
    close() {
        this.sinkProcessorNode.disconnect();
        this.gainNode.disconnect();
    }
    isPlaying() {
        return this.playing;
    }
    static setup(audioContext: AudioContext) {
        const destination = WebAudioSink.destination = audioContext.createMediaStreamDestination();
        const audioElement = WebAudioSink.audioElement = document.createElement('audio');
        audioElement.srcObject = destination.stream;
    }
    private processAudio(e: AudioProcessingEvent) {
        if (this.audioCache.available()) {
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
                e.outputBuffer.getChannelData(c).set(this.audioCache.silence);
            }
        }
    };
}
