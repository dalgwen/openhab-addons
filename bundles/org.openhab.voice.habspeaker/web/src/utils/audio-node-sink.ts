/**
 * The {@link AudioNodeSink} class plays the audio transmitted by an AudioNode using a MediaStreamDestination and a web AudioElement.
 */
export class AudioNodeSink {
    public static audioElement?: HTMLAudioElement;
    private static destination?: MediaStreamAudioDestinationNode;
    private gainNode: GainNode;
    protected playing: boolean = false;
    constructor(private id: string, private audioContext: AudioContext, private sinkProcessorNode: AudioNode, protected channels: number, volume: number, protected listener: (playing: boolean) => void) {
        this.gainNode = audioContext.createGain();
        this.gainNode.gain.value = (volume / 100);
    }
    static setup(audioContext: AudioContext) {
        const destination = AudioNodeSink.destination = audioContext.createMediaStreamDestination();
        const audioElement = AudioNodeSink.audioElement = document.createElement('audio');
        audioElement.srcObject = destination.stream;
    }
    start() {
        if (!AudioNodeSink.destination) {
            throw new Error('Sink was not setup');
        }
        this.sinkProcessorNode.connect(this.gainNode);
        this.gainNode.connect(AudioNodeSink.destination);
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
}
