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
    available(size: number) {
        return this.buffer.length >= size;
    }
    reset() {
        this.buffer = new Float32Array(0);
    }
}

export class SinkCache extends AudioWorkletProcessor {
    playing: boolean = false;
    audioCache = new AudioCache();
    constructor() {
        super();
        this.port.onmessage = (ev) => {
            if (ev.data instanceof Float32Array) {
                this.audioCache.writeAudioData(ev.data);
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
            const channelData = outputs[0][0];
            for(let i =0; i< bufferSize; i++) {
                channelData[i] = audioData[i];
            }
        } else {
            var length = audioData.byteLength / audioData.BYTES_PER_ELEMENT;
            for (let s = 0; s < length; s++) {
                // the channel index
                const c = s % channels;
                // the index inside the buffer channel
                const i = (s - c) / channels;
                const channelData = outputs[0][c] ?? new Float32Array(length / channels);
                channelData[i] = audioData[s];
            }
        }
        return true;
    }
}

registerProcessor("sink-cache", SinkCache);