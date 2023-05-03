import { AudioNodeSink } from "./audio-node-sink";
import webSinkWorkletUrl from "./audio-sink-worklet.ts?sharedworker&url";
/**
 * The {@link AudioSink} feeds the audio data send by a MessagePort to the audio system using an {@link AudioWorkletNode} instance and the async {@link AudioSinkWorklet} processor
 */
export class AudioSink extends AudioNodeSink {
    processorNode: AudioWorkletNode;
    constructor(id: string, audioContext: AudioContext, channels: number, volume: number, listener: (playing: boolean) => void) {
        const sinkProcessorNode = new AudioWorkletNode(audioContext, 'habspeaker-sink-worklet', { numberOfInputs: 0, numberOfOutputs: 1, outputChannelCount: [channels], channelCountMode: 'explicit' });
        super(id, audioContext, sinkProcessorNode, channels, volume, listener);
        this.processorNode = sinkProcessorNode;
    }
    setPort(port: MessagePort) {
        return new Promise<void>(resolve => {
            // transfer message port to processor node so audio doesn't use the main thread
            const setPortCommand = { type: 'audio_input_port', port };
            this.processorNode.port.postMessage(setPortCommand, [setPortCommand.port]);
            this.processorNode.port.onmessage = (ev) => {
                switch (ev.data.type) {
                    case "ready":
                        resolve();
                        break;
                    case "listening":
                        this.playing = ev.data.value;
                        this.listener(ev.data.value);
                        break;
                }
            }
        });
    }
    static async registerProcessor(audioContext: AudioContext) {
        await audioContext.audioWorklet.addModule(webSinkWorkletUrl);
    }
}
