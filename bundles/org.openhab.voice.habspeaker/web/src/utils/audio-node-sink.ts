/**
 * The {@link AudioNodeSink} class plays the audio transmitted by an AudioNode using a MediaStreamDestination and a web AudioElement.
 */
export class AudioNodeSink {
    private static audioElement?: HTMLAudioElement;
    private static destination?: MediaStreamAudioDestinationNode;
    private static connectedNodes = 0;
    private gainNode: GainNode;
    constructor(private id: string, private audioContext: AudioContext, private sinkProcessorNode: AudioNode, protected channels: number, volume: number) {
        this.gainNode = audioContext.createGain();
        this.gainNode.gain.value = (volume / 100);
    }
    static setupAudioElement(audioContext: AudioContext) {
        AudioNodeSink.destination = audioContext.createMediaStreamDestination();
        AudioNodeSink.audioElement = new Audio();
        AudioNodeSink.audioElement.srcObject = AudioNodeSink.destination.stream;
    }
    async start() {
        AudioNodeSink.connectedNodes++;
        this.sinkProcessorNode.connect(this.gainNode);
        if (!AudioNodeSink.destination || !AudioNodeSink.audioElement) {
            this.gainNode.connect(this.audioContext.destination);
        } else {
            this.gainNode.connect(AudioNodeSink.destination);
            if (AudioNodeSink.connectedNodes === 1) {
                await AudioNodeSink.audioElement.play();
            }
        }
    }
    getId() {
        return this.id;
    }
    setVolume(value: number) {
        this.gainNode.gain.setValueAtTime((value / 100), this.audioContext.currentTime);
    }
    close() {
        AudioNodeSink.connectedNodes--;
        if (AudioNodeSink.audioElement) {
            if (AudioNodeSink.connectedNodes === 0) {
                AudioNodeSink.audioElement.pause();
            }
        }
        this.sinkProcessorNode.disconnect();
        this.gainNode.disconnect();
    }
}
