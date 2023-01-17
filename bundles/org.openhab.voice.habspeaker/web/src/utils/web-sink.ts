import sinkCacheUrl from "./web-sink-worklet.ts?url";

export class WebAudioSink {
    private audioElement: HTMLAudioElement;
    private gainNode: GainNode;
    sinkProcessorNode: AudioWorkletNode;
    private playing: boolean = false;
    constructor(private id: string, private audioContext: AudioContext, channels: number, private listener: (playing: boolean) => void) {
        this.audioElement = document.createElement('audio');
        this.gainNode = audioContext.createGain();
        this.sinkProcessorNode = new AudioWorkletNode(audioContext, 'sink-cache', { numberOfInputs: 0, numberOfOutputs: 1, outputChannelCount: [channels], channelCountMode: 'explicit' });
        this.sinkProcessorNode.port.onmessage = (ev) => {
            const evData = ev.data;
            switch (evData.type) {
                case "listening":
                    this.listener(evData.value);
                    break;
            }
        }
        this.sinkProcessorNode.connect(this.gainNode);
        var destination = audioContext.createMediaStreamDestination();
        this.gainNode.connect(destination);
        this.audioElement.srcObject = destination.stream;
        this.audioElement.autoplay = true;
    }
    static async registerProcessor(audioContext: AudioContext) {
        await audioContext.audioWorklet.addModule(sinkCacheUrl);
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
    playAudio(buffer: Float32Array) {
        this.sinkProcessorNode.port.postMessage(buffer, [buffer.buffer]);
    }
    isPlaying() {
        return this.playing;
    }
}
