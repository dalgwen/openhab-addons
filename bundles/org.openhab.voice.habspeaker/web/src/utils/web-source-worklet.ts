const SOURCE_BUFFER_SIZE = 4096;
export class HABSpeakerSourceWorklet extends AudioWorkletProcessor {
    private buffer: Float32Array;
    constructor() {
        super();
        this.buffer = new Float32Array();
    }
    process(inputs: Float32Array[][], _: Float32Array[][]) {
        if (inputs[0].length) {
            // this is ready for only one channel
            const buffer = inputs[0][0];
            const merged = new Float32Array(this.buffer.length + buffer.length);
            merged.set(this.buffer, 0);
            merged.set(buffer, this.buffer.length);
            this.buffer = merged;
            while (this.buffer.length >= SOURCE_BUFFER_SIZE) {
                const chunk = this.buffer.slice(0, SOURCE_BUFFER_SIZE);
                this.buffer = this.buffer.slice(SOURCE_BUFFER_SIZE);
                const buffers: Float32Array[] = [chunk];
                this.port.postMessage(buffers, [buffers[0].buffer]);
            }
        }
        return true;
    }
}

registerProcessor("habspeaker-source-worklet", HABSpeakerSourceWorklet);