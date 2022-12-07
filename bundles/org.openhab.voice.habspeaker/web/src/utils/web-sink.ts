const BUFFER_SIZE = 2048;
class AudioCache {
    silence = new Float32Array(BUFFER_SIZE);
    buffer = new Float32Array(0);
    readAudioData(n: number) {
        var segment = this.buffer.subarray(0, n);
        this.buffer = this.buffer.subarray(n, this.buffer.length);
        return segment;
    }
    writeAudioData(buffer: Float32Array) {
        var currentCacheLength = this.buffer.length;
        var newBuffer = new Float32Array(currentCacheLength + buffer.length);
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

export class WebAudioSink {
    private audioElement: HTMLAudioElement;
    private gainNode: GainNode;
    sinkProcessorNode: ScriptProcessorNode;
    audioCache: AudioCache;
    private playing: boolean = false;
    constructor(private id: string, private audioContext: AudioContext, private channels: number, private listener: (playing: boolean) => void) {
        this.audioElement = document.createElement('audio');
        this.gainNode = audioContext.createGain();
        console.debug("main: stream volume: " + id);
        this.sinkProcessorNode = audioContext.createScriptProcessor(BUFFER_SIZE, 0, channels);
        this.sinkProcessorNode.onaudioprocess = this.processAudio.bind(this);
        this.sinkProcessorNode.connect(this.gainNode);
        this.audioCache = new AudioCache();
        var destination = audioContext.createMediaStreamDestination();
        this.gainNode.connect(destination);
        this.audioElement.srcObject = destination.stream;
        this.audioElement.autoplay = true;
    }
    getId() {
        return this.id;
    }
    setVolume(value: number) {
        this.gainNode.gain.setValueAtTime((value / 100), this.audioContext.currentTime);
    }
    close() {
        this.gainNode.disconnect();
        this.sinkProcessorNode.disconnect();
        this.audioElement.remove();
    }
    playAudio(buffer: Float32Array) {
        this.audioCache.writeAudioData(buffer);
    }
    isPlaying() {
        return this.playing;
    }
    private processAudio(e: AudioProcessingEvent) {
        if (this.audioCache.available()) {
            if (!this.playing) {
                this.playing = true;
                this.listener(true);
            }
            var audioData = this.audioCache.readAudioData(e.outputBuffer.length * this.channels);
            var channelsData: Float32Array[] = [];
            if (this.channels == 1) {
                channelsData[0] = audioData;
            } else {
                var length = audioData.byteLength / audioData.BYTES_PER_ELEMENT;
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
