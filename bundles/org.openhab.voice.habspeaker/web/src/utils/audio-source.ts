import audioSourceWorklet from "./audio-source-worklet.ts?sharedworker&url";
/**
 * Utility class to encapsulate the creation of a {@link MediaStreamAudioSourceNode}, and its connected {@link AudioNode} processors.
 */
export class AudioSource {
    private static audioContext?: AudioContext;
    private gainNode: GainNode;
    private stream?: MediaStream;
    private sourceNode?: MediaStreamAudioSourceNode;
    private nodeProcessors?: AudioNode[];

    constructor(volume: number) {
        this.gainNode = this.getAudioContext().createGain();
        this.setVolume(volume);
    }
    public static async configure(audioContext: AudioContext) {
        AudioSource.audioContext = audioContext;
        await audioContext.audioWorklet.addModule(audioSourceWorklet);
    }
    private async init() {
        if (!this.stream) {
            this.stream = await navigator.mediaDevices.getUserMedia({
                audio: {
                    autoGainControl: true,
                    echoCancellation: true,
                    noiseSuppression: true,
                },
                video: false,
            });
        }
        if (!this.sourceNode) {
            this.sourceNode = this.getAudioContext().createMediaStreamSource(this.stream);
            // connect processor to source
            this.sourceNode.connect(this.gainNode);
        }
    }
    isSuspended() {
        return this.getAudioContext().state !== 'running';
    }
    async resume() {
        if (this.getAudioContext().state !== 'running') {
            console.debug("main: resuming voice audio context");
            await this.getAudioContext().resume();
        }
        if (this.getAudioContext().state !== 'running') {
            console.debug("main: voice audio context not running");
        } else if (!this.stream || !this.stream.active) {
            console.debug("main: recreating audio media stream");
            this.stream = undefined;
            this.sourceNode?.disconnect();
            this.sourceNode = undefined;
            await this.init();
        }
    }
    async start(...audioProcessors: AudioNode[]) {
        await this.resume();
        const currentProcessors = this.nodeProcessors ?? [];
        audioProcessors
            .filter(p => !currentProcessors.includes(p))
            .forEach((audioNode) => this.connectNode(audioNode));
        currentProcessors
            .filter(p => !audioProcessors.includes(p))
            .forEach((audioNode) => this.disconnectNode(audioNode));
        this.nodeProcessors = audioProcessors;
        console.debug(`main: ${this.nodeProcessors.length} active audio source processors`);
    }
    setVolume(value: number) {
        this.gainNode.gain.setValueAtTime((value / 100), this.getAudioContext().currentTime);
    }
    stop() {
        if (this.nodeProcessors) {
            for (const audioNode of this.nodeProcessors) {
                this.disconnectNode(audioNode);
            }
            this.nodeProcessors = undefined;
        }
        console.debug('main: no active audio source processors');
    }
    private connectNode(audioNode: AudioNode) {
        this.gainNode.connect(audioNode);
        if (audioNode.numberOfOutputs > 0) {
            audioNode.connect(this.getAudioContext().destination);
        }
    }
    private disconnectNode(audioNode: AudioNode) {
        this.gainNode.disconnect(audioNode);
        if (audioNode.numberOfOutputs > 0) {
            audioNode.disconnect(this.getAudioContext().destination);
        }
    }
    protected getAudioContext() {
        if (!AudioSource.audioContext) {
            throw new Error("Sink class must be initialized")
        }
        return AudioSource.audioContext;
    }
}
