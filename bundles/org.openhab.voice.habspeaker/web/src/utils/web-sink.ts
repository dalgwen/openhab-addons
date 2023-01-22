import sinkCacheUrl from "./web-sink-worklet.ts?sharedworker&url";
/**
 * Utility class to encapsulate the creation of a media stream destination consumed by an audio element
 * and connected to an audio worklet which will forward the audio buffers received though the provided port
 * into the audio system.
 */
export class WebAudioSink {
    private audioElement: HTMLAudioElement;
    private gainNode: GainNode;
    sinkProcessorNode: AudioWorkletNode;
    private playing: boolean = false;
    private destination: MediaStreamAudioDestinationNode;
    constructor(private id: string, private audioContext: AudioContext, channels: number, audioMessagePort: MessagePort, volume: number, private listener: (playing: boolean) => void) {
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
        this.destination = audioContext.createMediaStreamDestination();
        this.gainNode.connect(this.destination);
        this.audioElement = document.createElement('audio');
        this.audioElement.srcObject = this.destination.stream;
        this.audioElement.autoplay = true;
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
        this.destination.disconnect();
        this.destination.stream.getTracks().forEach(t => t.stop());
        this.audioElement.remove();
    }
    isPlaying() {
        return this.playing;
    }
    static async registerProcessor(audioContext: AudioContext) {
        await audioContext.audioWorklet.addModule(sinkCacheUrl);
    }
}
