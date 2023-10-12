import { ConverterType, create as createResamplerWASM } from "@alexanderolsen/libsamplerate-js";
import type { SRC as ResamplerWASMImpl } from "@alexanderolsen/libsamplerate-js/dist/src";
export async function createResampler(resampleMode: string, inputSampleRate: number, outputSampleRate: number, channels: number) {
    if (inputSampleRate === outputSampleRate) {
        console.debug("No resampling needed for this stream")
        return new ResamplerNoop();
    }
    const resampler: Resampler = new ResamplerWasm(inputSampleRate, outputSampleRate, channels, resampleMode);
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
export class ResamplerWasm implements Resampler {
    resamplerImpl!: ResamplerWASMImpl;
    constructor(private sampleRate: number, private targetSampleRate: number, private channels: number, private resampleMode: string) { }
    async init(): Promise<void> {
        const converterType = (ConverterType as { [key: string]: typeof ConverterType.SRC_LINEAR })[this.resampleMode] ?? ConverterType.SRC_LINEAR;
        this.resamplerImpl = await createResamplerWASM(this.channels, this.sampleRate, this.targetSampleRate, { converterType });
    }
    resample(samples: Float32Array): Float32Array {
        return this.resamplerImpl.full(samples);
    }
    close(): void {
        this.resamplerImpl.destroy();
    }
}
