import { CircularBufferExecutor } from "./circular-buffer";

const AUDIO_BUFFER_SIZE = 4096;
/**
 * The class {@link AudioSourceWorklet}, is an {@link AudioWorkletProcessor} implementation that sends the incoming audio data through a {@link MessagePort}.
 */
export class AudioSourceWorklet extends AudioWorkletProcessor {
    private circularBufferExecutor = new CircularBufferExecutor(new Float32Array(AUDIO_BUFFER_SIZE), (buffer) => this.sendAudioMessage(buffer));
    process(inputs: Float32Array[][]) {
        if (inputs[0].length) {
            // this is ready for only one channel
            this.circularBufferExecutor.process(inputs[0][0]);
        }
        return true;
    }
    sendAudioMessage(audioBuffer: Float32Array) {
        this.port.postMessage(audioBuffer);
    }
}

registerProcessor("habspeaker-source-worklet", AudioSourceWorklet);