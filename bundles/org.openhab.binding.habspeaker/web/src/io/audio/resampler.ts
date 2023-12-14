import { resample } from "wave-resampler";
export async function createResampler(resampleMode: string, inputSampleRate: number, outputSampleRate: number, channels: number) {
    if (inputSampleRate === outputSampleRate) {
        console.debug("No resampling needed for this stream")
        return new ResamplerNoop();
    }
    const resampler: Resampler = new ResamplerJS(inputSampleRate, outputSampleRate, channels, resampleMode);
    await resampler.init();
    return resampler;
}
export interface Resampler {
    init(): Promise<void>;
    resample(samples: Float32Array): Float32Array;
    close(): void;
}
export class ResamplerNoop implements Resampler {
    init(): Promise<void> {
        return Promise.resolve();
    }
    resample(samples: Float32Array): Float32Array {
        return samples;
    }
    close(): void {

    }
}
export class ResamplerJS implements Resampler {
    private readonly resampleMethod: string;
    constructor(private sampleRate: number, private targetSampleRate: number, private channels: number, resampleMode: string) { 
        this.resampleMethod = this.getMethod(resampleMode);
    }

    init(): Promise<void> {
        return Promise.resolve();
    }
    resample(samples: Float32Array): Float32Array {
        const deinterlacedAudio: (Float32Array | Float64Array)[] = [];
        const samplesPerChannel = Math.floor(samples.length / this.channels);
        for (let sampleN = 0; sampleN < samplesPerChannel; sampleN++) {
            for (let channel = 0; channel < this.channels; channel++) {
                const audioChannel = deinterlacedAudio[channel] ?? (deinterlacedAudio[channel] = new Float32Array(samplesPerChannel));
                audioChannel[sampleN] = samples[(sampleN * this.channels) + channel];
            }
        }
        for (let i = 0; i < deinterlacedAudio.length; i++) {
            deinterlacedAudio[i] = <Float64Array>resample(deinterlacedAudio[i], this.sampleRate, this.targetSampleRate, { method: this.resampleMethod });
        }
        const interlacedAudio = new Float32Array(deinterlacedAudio.reduce((t, audioChannel) => t + audioChannel.length, 0));
        for (let sampleN = 0; sampleN < deinterlacedAudio[0].length; sampleN++) {
            for (let channel = 0; channel < this.channels; channel++) {
                interlacedAudio[(sampleN * this.channels) + channel] = deinterlacedAudio[channel][sampleN];
            }
        }
        return interlacedAudio;
    }
    private getMethod(resampleMode: string) {
        const allowedMethods = ['point', 'linear', 'cubic', 'sinc'];
        return allowedMethods.includes(resampleMode.toLocaleLowerCase()) ? resampleMode.toLocaleLowerCase() : allowedMethods[0];
    }
    close(): void {

    }

}