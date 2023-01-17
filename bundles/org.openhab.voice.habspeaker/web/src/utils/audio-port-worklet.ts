export class AudioPortWorklet extends AudioWorkletProcessor {
    playing: boolean = false;
    audioMessagePort?: MessagePort;
    constructor() {
        super();
        this.port.onmessage = (ev) => {
            if (ev.data.type === 'audio_output_port' && ev.data.port instanceof MessagePort) {
                this.audioMessagePort = ev.data.port;
            }
        };
    }
    process(inputs: Float32Array[][], _: Float32Array[][]) {
        if (inputs[0].length) {
            // this is ready for only one channel
            const buffers: Float32Array[] = [inputs[0][0].slice()];
            this.audioMessagePort?.postMessage(buffers, [buffers[0].buffer]);
            return true;
        }
        return false;
    }
}

registerProcessor("audio-port", AudioPortWorklet);