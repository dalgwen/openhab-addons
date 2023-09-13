import webSinkWorkletUrl from "./audio-sink-worklet.ts?sharedworker&url";
/**
 * The {@link AudioSink} class plays the audio transmitted by an AudioNode using the audioContext destination or a MediaStreamDestination + a web AudioElement.
 */
export class AudioSink {
    private static audioContext?: AudioContext;
    private static connectedNodes = 0;
    private static audioElement?: HTMLAudioElement;
    private static destination?: MediaStreamAudioDestinationNode;

    private gainNode: GainNode;
    private processorNode: AudioWorkletNode;

    constructor(private id: string, protected channels: number, volume: number) {
        this.processorNode = new AudioWorkletNode(this.getAudioContext(), 'habspeaker-sink-worklet', { numberOfInputs: 0, numberOfOutputs: 1, outputChannelCount: [channels], channelCountMode: 'explicit' });
        this.gainNode = this.getAudioContext().createGain();
        this.gainNode.gain.value = (volume / 100);
    }
    static async configure(audioContext: AudioContext, useAudioElement: boolean) {
        await audioContext.audioWorklet.addModule(webSinkWorkletUrl);
        AudioSink.audioContext = audioContext;
        if (useAudioElement) {
            if (!AudioSink.destination) {
                AudioSink.destination = audioContext.createMediaStreamDestination();
            }
            if (!AudioSink.audioElement) {
                AudioSink.audioElement = new Audio();
                AudioSink.audioElement.srcObject = AudioSink.destination.stream;
            }
        } else {
            AudioSink.destination?.stream.getTracks().forEach(t => t.stop());
            AudioSink.destination = undefined;
            AudioSink.audioElement?.remove();
            AudioSink.audioElement = undefined;
        }
    }
    async start() {
        AudioSink.connectedNodes++;
        this.processorNode.connect(this.gainNode);
        if (!AudioSink.destination || !AudioSink.audioElement) {
            this.gainNode.connect(this.getAudioContext().destination);
        } else {
            this.gainNode.connect(AudioSink.destination);
            if (AudioSink.connectedNodes === 1) {
                await AudioSink.audioElement.play();
            }
        }
    }
    getId() {
        return this.id;
    }
    setVolume(value: number) {
        this.gainNode.gain.setValueAtTime((value / 100), this.getAudioContext().currentTime);
    }
    close() {
        AudioSink.connectedNodes--;
        if (AudioSink.audioElement) {
            if (AudioSink.connectedNodes === 0) {
                AudioSink.audioElement.pause();
            }
        }
        this.processorNode.disconnect();
        this.gainNode.disconnect();
    }
    getMessagePort() {
        return this.processorNode.port;
    }
    protected getAudioContext() {
        if (!AudioSink.audioContext) {
            throw new Error("Sink class must be initialized")
        }
        return AudioSink.audioContext;
    }
}
