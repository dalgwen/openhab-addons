import { ConverterType, create as createResamplerWASM } from "@alexanderolsen/libsamplerate-js";
import type { SRC as ResamplerWASMImpl } from "@alexanderolsen/libsamplerate-js/dist/src";
export async function createResampler(resampleMode: string, inputSampleRate: number, outputSampleRate: number, channels: number, inputBufferSize: number) {
    if (inputSampleRate === outputSampleRate) {
        console.debug("No resampling needed for this stream")
        return new ResamplerNoop();
    }
    let resampler: Resampler;
    switch (resampleMode) {
        case "wasm_sinc_best_quality":
        case "wasm_sinc_medium_quality":
        case "wasm_sinc_fastest":
        case "wasm_zero_order_hold":
        case "wasm_linear":
            console.debug("Using wasm resampler");
            resampler = new ResamplerWasm(inputSampleRate, outputSampleRate, channels, resampleMode);
            break;
        case "js_default":
            console.debug("Using js resampler");
            resampler = new ResamplerJS(inputSampleRate, outputSampleRate, channels, inputBufferSize);
            break;
        default:
            console.warn("Unsupported resampler mode falling back to js implementation");
            resampler = new ResamplerJS(inputSampleRate, outputSampleRate, channels, inputBufferSize);
    }
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
        this.resamplerImpl = await createResamplerWASM(this.channels, this.sampleRate, this.targetSampleRate, { converterType: this.getConverterType() });
    }
    resample(samples: Float32Array): Float32Array {
        return this.resamplerImpl.full(samples);
    }
    close(): void {
        this.resamplerImpl.destroy();
    }
    private getConverterType() {
        switch (this.resampleMode) {
            case "wasm_sinc_best_quality":
                return ConverterType.SRC_SINC_BEST_QUALITY;
            case "wasm_sinc_medium_quality":
                return ConverterType.SRC_SINC_MEDIUM_QUALITY;
            case "wasm_sinc_fastest":
                return ConverterType.SRC_SINC_FASTEST;
            case "wasm_zero_order_hold":
                return ConverterType.SRC_ZERO_ORDER_HOLD;
            case "wasm_linear":
                return ConverterType.SRC_LINEAR;
            default:
                console.warn("Invalid resample mode:", this.resampleMode);
                console.warn("Using linear resample mode");
                return ConverterType.SRC_LINEAR;
        }
    }
}

// based on https://github.com/taisel/XAudioJS/blob/master/resampler.js
export class ResamplerJS implements Resampler {
    private fromSampleRate: number;
    private toSampleRate: number;
    private channels: number;
    private inputBufferSize: number;
    private resampler!: (buffer: Float32Array) => Float32Array;
    private ratioWeight: number = 1;
    private lastWeight: number = -1;
    private tailExists: boolean = false;
    private outputBuffer!: Float32Array;
    private outputBufferSize: any;
    private lastOutput!: Float32Array;
    constructor(fromSampleRate: number, toSampleRate: number, channels: number, inputBufferSize: number) {

        if (!fromSampleRate || !toSampleRate || !channels) {
            throw (new Error("Invalid settings specified for the resampler."));
        }
        this.fromSampleRate = fromSampleRate;
        this.toSampleRate = toSampleRate;
        this.channels = channels || 0;
        this.inputBufferSize = inputBufferSize;
    }
    async init(): Promise<void> {
        if (this.fromSampleRate == this.toSampleRate) {
            this.resampler = (buffer: Float32Array) => buffer;
        } else {

            if (this.fromSampleRate < this.toSampleRate) {
                this.resampler = this.linearInterpolation();
                this.lastWeight = 1;

            } else {
                this.resampler = this.multiTap();
                this.tailExists = false;
                this.lastWeight = 0;
            }
            this.initializeBuffers();
            this.ratioWeight = this.fromSampleRate / this.toSampleRate;
        }
    }
    close(): void {

    }

    initializeBuffers() {
        this.outputBufferSize = (Math.ceil(this.inputBufferSize * this.toSampleRate / this.fromSampleRate / this.channels * 1.000000476837158203125) + this.channels) + this.channels;
        this.outputBuffer = new Float32Array(this.outputBufferSize);
        this.lastOutput = new Float32Array(this.channels);
    }

    resample(buffer: Float32Array) {
        if (this.fromSampleRate == this.toSampleRate) {
            this.ratioWeight = 1;
        } else {
            if (this.fromSampleRate < this.toSampleRate) {
                this.lastWeight = 1;
            } else {
                this.tailExists = false;
                this.lastWeight = 0;
            }
            this.initializeBuffers();
            this.ratioWeight = this.fromSampleRate / this.toSampleRate;
        }
        return this.resampler(buffer)
    }

    private bufferSlice(sliceAmount: number) {
        return this.outputBuffer.subarray(0, sliceAmount);
    }

    private linearInterpolation() {
        return (buffer: Float32Array) => {
            let bufferLength = buffer.length,
                channels = this.channels,
                outLength,
                ratioWeight,
                weight,
                firstWeight,
                secondWeight,
                sourceOffset,
                outputOffset,
                outputBuffer,
                channel;

            if ((bufferLength % channels) !== 0) {
                throw (new Error("Buffer was of incorrect sample length."));
            }
            if (bufferLength <= 0) {
                return new Float32Array();
            }

            outLength = this.outputBufferSize;
            ratioWeight = this.ratioWeight;
            weight = this.lastWeight;
            firstWeight = 0;
            secondWeight = 0;
            sourceOffset = 0;
            outputOffset = 0;
            outputBuffer = this.outputBuffer;

            for (; weight < 1; weight += ratioWeight) {
                secondWeight = weight % 1;
                firstWeight = 1 - secondWeight;
                this.lastWeight = weight % 1;
                for (channel = 0; channel < this.channels; ++channel) {
                    outputBuffer[outputOffset++] = (this.lastOutput[channel] * firstWeight) + (buffer[channel] * secondWeight);
                }
            }
            weight -= 1;
            for (bufferLength -= channels, sourceOffset = Math.floor(weight) * channels; outputOffset < outLength && sourceOffset < bufferLength;) {
                secondWeight = weight % 1;
                firstWeight = 1 - secondWeight;
                for (channel = 0; channel < this.channels; ++channel) {
                    outputBuffer[outputOffset++] = (buffer[sourceOffset + ((channel > 0) ? (channel) : 0)] * firstWeight) + (buffer[sourceOffset + (channels + channel)] * secondWeight);
                }
                weight += ratioWeight;
                sourceOffset = Math.floor(weight) * channels;
            }
            for (channel = 0; channel < channels; ++channel) {
                this.lastOutput[channel] = buffer[sourceOffset++];
            }
            return this.bufferSlice(outputOffset);
        };
    }

    private multiTap() {
        return (buffer: Float32Array) => {
            let bufferLength = buffer.length,
                outLength,
                output_variable_list,
                channels = this.channels,
                ratioWeight,
                weight,
                channel,
                actualPosition,
                amountToNext,
                alreadyProcessedTail,
                outputBuffer,
                outputOffset,
                currentPosition;

            if ((bufferLength % channels) !== 0) {
                throw (new Error("Buffer was of incorrect sample length."));
            }
            if (bufferLength <= 0) {
                return new Float32Array();
            }

            outLength = this.outputBufferSize;
            output_variable_list = [];
            ratioWeight = this.ratioWeight;
            weight = 0;
            actualPosition = 0;
            amountToNext = 0;
            alreadyProcessedTail = !this.tailExists;
            this.tailExists = false;
            outputBuffer = this.outputBuffer;
            outputOffset = 0;
            currentPosition = 0;

            for (channel = 0; channel < channels; ++channel) {
                output_variable_list[channel] = 0;
            }

            do {
                if (alreadyProcessedTail) {
                    weight = ratioWeight;
                    for (channel = 0; channel < channels; ++channel) {
                        output_variable_list[channel] = 0;
                    }
                } else {
                    weight = this.lastWeight;
                    for (channel = 0; channel < channels; ++channel) {
                        output_variable_list[channel] = this.lastOutput[channel];
                    }
                    alreadyProcessedTail = true;
                }
                while (weight > 0 && actualPosition < bufferLength) {
                    amountToNext = 1 + actualPosition - currentPosition;
                    if (weight >= amountToNext) {
                        for (channel = 0; channel < channels; ++channel) {
                            output_variable_list[channel] += buffer[actualPosition++] * amountToNext;
                        }
                        currentPosition = actualPosition;
                        weight -= amountToNext;
                    } else {
                        for (channel = 0; channel < channels; ++channel) {
                            output_variable_list[channel] += buffer[actualPosition + ((channel > 0) ? channel : 0)] * weight;
                        }
                        currentPosition += weight;
                        weight = 0;
                        break;
                    }
                }

                if (weight === 0) {
                    for (channel = 0; channel < channels; ++channel) {
                        outputBuffer[outputOffset++] = output_variable_list[channel] / ratioWeight;
                    }
                } else {
                    this.lastWeight = weight;
                    for (channel = 0; channel < channels; ++channel) {
                        this.lastOutput[channel] = output_variable_list[channel];
                    }
                    this.tailExists = true;
                    break;
                }
            } while (actualPosition < bufferLength && outputOffset < outLength);
            return this.bufferSlice(outputOffset);
        };
    }

}
