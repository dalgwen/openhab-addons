export class HABSpeakerSourceWorklet extends AudioWorkletProcessor {
    constructor() {
        super();
    }
    process(inputs: Float32Array[][], _: Float32Array[][]) {
        if (inputs[0].length) {
            // this is ready for only one channel
            const buffers: Float32Array[] = [inputs[0][0].slice()];
            this.port.postMessage(buffers, [buffers[0].buffer]);
            return true;
        }
        return true;
    }
}

registerProcessor("habspeaker-source-worklet", HABSpeakerSourceWorklet);