import webSinkWorkletUrl from "./audio-sink-worklet.ts?sharedworker&url";
/**
 * The {@link AudioSink} class plays the audio transmitted by an AudioNode using the audioContext destination or a MediaStreamDestination + a web AudioElement.
 */
export class AudioSink {
    private static audioContext?: AudioContext;
    private static connectedNodes = 0;
    private static destination?: MediaStreamAudioDestinationNode;
    private static audioElement?: HTMLAudioElement;

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
            console.debug("AudioSink: Using audio element to render sound");
            if (!AudioSink.destination) {
                AudioSink.destination = audioContext.createMediaStreamDestination();
            }
            if (!AudioSink.audioElement) {
                AudioSink.audioElement = new Audio();
                AudioSink.audioElement.srcObject = AudioSink.destination.stream;
            }
        } else {
            console.debug("AudioSink: Using audio context destination to render sound");
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
                console.debug("AudioSink: Play audio element");
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
        const audioElement = AudioSink.audioElement;
        if (audioElement) {
            if (AudioSink.connectedNodes === 0) {
                console.debug("AudioSink: Pause audio element");
                audioElement.pause();
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
