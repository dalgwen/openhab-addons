/**
 * The {@link AudioNodeSink} class plays the audio transmitted by an AudioNode using a MediaStreamDestination and a web AudioElement.
 */
export class AudioNodeSink {
    private static audioElement?: HTMLAudioElement;
    private static destination?: MediaStreamAudioDestinationNode;
    private static connectedNodes = 0;
    private gainNode: GainNode;
    protected playing: boolean = false;
    constructor(private id: string, private audioContext: AudioContext, private sinkProcessorNode: AudioNode, protected channels: number, volume: number) {
        this.gainNode = audioContext.createGain();
        this.gainNode.gain.value = (volume / 100);
    }
    static setup(audioContext: AudioContext) {
        AudioNodeSink.destination = audioContext.createMediaStreamDestination();
        AudioNodeSink.audioElement = new Audio();
        AudioNodeSink.audioElement.srcObject = AudioNodeSink.destination.stream;
    }
    async start() {
        if (!AudioNodeSink.destination || !AudioNodeSink.audioElement) {
            throw new Error('Sink was not setup');
        }
        AudioNodeSink.connectedNodes++;
        this.sinkProcessorNode.connect(this.gainNode);
        this.gainNode.connect(AudioNodeSink.destination);
        if (AudioNodeSink.connectedNodes === 1) {
            await AudioNodeSink.audioElement.play();
        }
    }
    getId() {
        return this.id;
    }
    setVolume(value: number) {
        this.gainNode.gain.setValueAtTime((value / 100), this.audioContext.currentTime);
    }
    close() {
        if (!AudioNodeSink.destination || !AudioNodeSink.audioElement) {
            throw new Error('Sink was not setup');
        }
        AudioNodeSink.connectedNodes--;
        if (AudioNodeSink.connectedNodes === 0) {
            AudioNodeSink.audioElement.pause();
        }
        this.sinkProcessorNode.disconnect();
        this.gainNode.disconnect();
    }
    isPlaying() {
        return this.playing;
    }
}
