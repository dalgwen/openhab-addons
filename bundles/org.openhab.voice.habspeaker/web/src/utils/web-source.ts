export class WebAudioSource {
    private micProcessorNode: ScriptProcessorNode;
    private stream?: MediaStream;
    private sourceNode?: MediaStreamAudioSourceNode;
    constructor(private audioContext: AudioContext, private onAudioBuffer: (buffers: Float32Array[]) => void) {
        this.micProcessorNode = audioContext.createScriptProcessor(4096, 1, 1);
        this.micProcessorNode.onaudioprocess = this.processAudio.bind(this);
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
        if (!this.sourceNode && this.audioContext && this.micProcessorNode) {
            this.sourceNode = this.audioContext.createMediaStreamSource(this.stream);
            // connect processor to source
            // Add a gain node to the microphone to reduce noise
            var micGainNode = this.audioContext.createGain();
            micGainNode.gain.value = 0.5;
            micGainNode.connect(this.micProcessorNode);
            this.sourceNode.connect(micGainNode);
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
    async start() {
        await this.resume();
        this.micProcessorNode.connect(this.audioContext.destination);
    }
    stop() {
        this.micProcessorNode.disconnect(this.audioContext.destination);
    }
    private processAudio({ inputBuffer }: AudioProcessingEvent) {
        const buffers: Float32Array[] = [];
        for (let i = 0; i < inputBuffer.numberOfChannels; i++) {
            buffers[i] = inputBuffer.getChannelData(i);
        }
        this.onAudioBuffer(buffers);
    }
}
