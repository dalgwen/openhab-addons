import { AudioNodeSink } from "./audio-node-sink";
import webSinkWorkletUrl from "./audio-sink-worklet.ts?sharedworker&url";
/**
 * The {@link AudioSink} feeds the audio data send by a MessagePort to the audio system using an {@link AudioWorkletNode} instance and the async {@link AudioSinkWorklet} processor
 */
export class AudioSink extends AudioNodeSink {
    processorNode: AudioWorkletNode;
    constructor(id: string, audioContext: AudioContext, channels: number, volume: number) {
        const sinkProcessorNode = new AudioWorkletNode(audioContext, 'habspeaker-sink-worklet', { numberOfInputs: 0, numberOfOutputs: 1, outputChannelCount: [channels], channelCountMode: 'explicit' });
        super(id, audioContext, sinkProcessorNode, channels, volume);
        this.processorNode = sinkProcessorNode;
    }
    getMessagePort() {
        return this.processorNode.port;
    }
    static async registerProcessor(audioContext: AudioContext) {
        await audioContext.audioWorklet.addModule(webSinkWorkletUrl);
    }
}
