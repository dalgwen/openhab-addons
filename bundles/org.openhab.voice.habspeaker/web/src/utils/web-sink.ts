import webSinkWorkletUrl from "./web-sink-worklet.ts?sharedworker&url";
/**
 * Utility class to encapsulate the creation of a media stream destination consumed by an audio element
 * and connected to an audio worklet which will forward the audio buffers received though the provided port
 * into the audio system.
 */
export class WebAudioSink {
    public static audioElement?: HTMLAudioElement;
    private static destination?: MediaStreamAudioDestinationNode;
    private gainNode: GainNode;
    sinkProcessorNode: AudioWorkletNode;
    private playing: boolean = false;
    constructor(private id: string, private audioContext: AudioContext, channels: number, audioMessagePort: MessagePort, volume: number, private listener: (playing: boolean) => void) {
        if (!WebAudioSink.destination) {
            throw new Error('Sink was not setup');
        }
        this.gainNode = audioContext.createGain();
        this.gainNode.gain.value = (volume / 100);
        this.sinkProcessorNode = new AudioWorkletNode(audioContext, 'habspeaker-sink-worklet', { numberOfInputs: 0, numberOfOutputs: 1, outputChannelCount: [channels], channelCountMode: 'explicit' });
        // transfer message port to processor node so audio doesn't use the main thread
        const setPortCommand = { type: 'audio_input_port', port: audioMessagePort };
        this.sinkProcessorNode.port.postMessage(setPortCommand, [setPortCommand.port]);
        this.sinkProcessorNode.port.onmessage = (ev) => {
            switch (ev.data.type) {
                case "listening":
                    this.playing = ev.data.value;
                    this.listener(ev.data.value);
                    break;
            }
        }
        this.sinkProcessorNode.connect(this.gainNode);
        this.gainNode.connect(WebAudioSink.destination);
    }
    static setup(audioContext: AudioContext) {
        const destination = WebAudioSink.destination = audioContext.createMediaStreamDestination();
        const audioElement = WebAudioSink.audioElement = document.createElement('audio');
        audioElement.srcObject = destination.stream;
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
    static async registerProcessor(audioContext: AudioContext) {
        await audioContext.audioWorklet.addModule(webSinkWorkletUrl);
    }
}
