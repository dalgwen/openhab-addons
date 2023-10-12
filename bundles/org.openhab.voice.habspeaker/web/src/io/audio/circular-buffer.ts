type TypedArray = Int8Array | Uint8Array | Uint8ClampedArray | Int16Array | Uint16Array | Int32Array | Uint32Array | Float32Array;
/**
 * This utility reuses the same buffer to run the callback ensuring a constant buffer size.
 * It can be feed with buffers of any chunk size.
 */
export class CircularBufferExecutor<TypedArrayT extends TypedArray> {
    private offset = 0;
    private cloneData: boolean;
    constructor(private buffer: TypedArrayT, private cb: (buffer: TypedArrayT) => (void | Promise<void>), options?: { clone?: boolean }) {
        this.cloneData = options?.clone ?? false
    }
    /**
     * Process this chunk.
     * Take in account the inner operation be run zero, one or multiple times, depending on the chunk length and the offset of the internal buffer. 
     * @param chunk typed array of the same type of the buffer.
     */
    async process(chunk: TypedArrayT) {
        // this is ready for only one channel
        const requiredSamples = this.buffer.length - this.offset;
        if (chunk.length >= requiredSamples) {
            this.buffer.set(chunk.subarray(0, requiredSamples), this.offset);
            const result = this.cb(this.getBuffer());
            if (result) await result;
            const remaining = chunk.subarray(requiredSamples);
            if (remaining.length >= this.buffer.length) {
                this.offset = 0;
                await this.process(chunk);
            } else if (remaining.length > 0) {
                this.offset = remaining.length;
                this.buffer.set(remaining, 0);
            } else {
                this.offset = 0;
            }
        } else {
            this.buffer.set(chunk, this.offset);
            this.offset = this.offset + chunk.length;
        }
    }
    private getBuffer(): TypedArrayT {
        if (this.cloneData) {
            return this.buffer.slice() as TypedArrayT;
        }
        return this.buffer;
    }
}