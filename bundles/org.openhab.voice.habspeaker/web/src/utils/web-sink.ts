import sinkCacheUrl from "./web-sink-worklet.ts?sharedworker&url";

export class WebAudioSink {
    private audioElement: HTMLAudioElement;
    private gainNode: GainNode;
    sinkProcessorNode: AudioWorkletNode;
    private playing: boolean = false;
    constructor(private id: string, private audioContext: AudioContext, channels: number, audioMessagePort: MessagePort, private listener: (playing: boolean) => void) {
        this.audioElement = document.createElement('audio');
        this.gainNode = audioContext.createGain();
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
        var destination = audioContext.createMediaStreamDestination();
        this.gainNode.connect(destination);
        this.audioElement.srcObject = destination.stream;
        this.audioElement.autoplay = true;
    }
    getId() {
        return this.id;
    }
    setVolume(value: number) {
        this.gainNode.gain.setValueAtTime((value / 100), this.audioContext.currentTime);
    }
    close() {
        this.gainNode.disconnect();
        this.sinkProcessorNode.disconnect();
        this.audioElement.remove();
    }
    isPlaying() {
        return this.playing;
    }
    static async registerProcessor(audioContext: AudioContext) {
        await audioContext.audioWorklet.addModule(sinkCacheUrl);
    }
}
