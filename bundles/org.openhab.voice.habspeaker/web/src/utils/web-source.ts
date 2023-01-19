export class WebAudioSource {
    private stream?: MediaStream;
    private sourceNode?: MediaStreamAudioSourceNode;
    private micGainNode: GainNode;
    private nodeProcessors?: AudioNode[];
    constructor(private audioContext: AudioContext) {
        // Add a gain node to the microphone to reduce noise
        this.micGainNode = this.audioContext.createGain();
        // TODO: customize this
        this.micGainNode.gain.value = 0.75;
    }
    private async init() {
        if (!this.stream) {
            this.stream = await navigator.mediaDevices.getUserMedia({
                audio: {
                    autoGainControl: true,
                    echoCancellation: true,
                    noiseSuppression: true,
                    suppressLocalAudioPlayback: false,
                },
                video: false,
            });
        }
        if (!this.sourceNode && this.audioContext) {
            this.sourceNode = this.audioContext.createMediaStreamSource(this.stream);
            // connect processor to source
            this.sourceNode.connect(this.micGainNode);
        }
    }
    async resume() {
        if (this.audioContext.state !== 'running') {
            console.debug("main: resuming microphone audio context");
            await this.audioContext.resume();
        }
        if (this.audioContext.state !== 'running') {
            console.debug("main: microphone audio context not running");
        } else if (!this.stream || !this.stream.active) {
            console.debug("main: recreating microphone stream");
            this.stream = undefined;
            if (this.sourceNode) this.sourceNode.disconnect();
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
        this.micGainNode.connect(audioNode);
        if (audioNode.numberOfOutputs > 0) {
            audioNode.connect(this.audioContext.destination);
        }
    }
    private disconnectNode(audioNode: AudioNode) {
        this.micGainNode.disconnect(audioNode);
        if (audioNode.numberOfOutputs > 0) {
            audioNode.disconnect(this.audioContext.destination);
        }
    }
}
