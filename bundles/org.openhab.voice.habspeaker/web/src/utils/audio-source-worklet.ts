const AUDIO_BUFFER_SIZE = 4096;
/**
 * The class {@link AudioSourceWorklet}, is an {@link AudioWorkletProcessor} implementation that sends the incoming audio data through a {@link MessagePort}.
 */
export class AudioSourceWorklet extends AudioWorkletProcessor {
    private samples: Float32Array;
    private samplesOffset: number;
    constructor() {
        super();
        this.samples = new Float32Array(AUDIO_BUFFER_SIZE);
        this.samplesOffset = 0;
    }
    process(inputs: Float32Array[][]) {
        if (inputs[0].length) {
            // this is ready for only one channel
            const channelBuffer = inputs[0][0];
            const requiredSamples = AUDIO_BUFFER_SIZE - this.samplesOffset;
            if (channelBuffer.length >= requiredSamples) {
                this.samples.set(channelBuffer.subarray(0, requiredSamples), this.samplesOffset);
                this.port.postMessage([this.samples]);
                const remaining = channelBuffer.subarray(requiredSamples);
                if (remaining.length >= AUDIO_BUFFER_SIZE) {
                    this.samplesOffset = 0;
                    this.process([[remaining]]);
                } else if (remaining.length > 0) {
                    this.samplesOffset = remaining.length;
                    this.samples.set(remaining, 0);
                } else {
                    this.samplesOffset = 0;
                }
            } else {
                this.samples.set(channelBuffer, this.samplesOffset);
                this.samplesOffset = this.samplesOffset + channelBuffer.length;
            }
        }
        return true;
    }
}

registerProcessor("habspeaker-source-worklet", AudioSourceWorklet);