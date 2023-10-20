/**
 * Utility class to cache and consume audio chunks
 */
export class AudioCache {
    private buffers: Float32Array[] = [];
    private offset = 0;
    private length = 0;
    readAudioData(n: number) {
        this.length -= n;
        const currentBuffer = this.buffers[0];
        const nextOffset = n + this.offset;
        if (nextOffset <= currentBuffer.length) {
            const chunk = currentBuffer.subarray(this.offset, this.offset + n);
            if (nextOffset < currentBuffer.length) {
                this.offset = nextOffset;
            } else {
                this.offset = 0;
                this.buffers.shift();
            }
            return chunk;
        } else {
            const partialChunks = [currentBuffer.subarray(this.offset)];
            this.buffers.shift();
            let required = n - partialChunks[0].length;
            while (required > 0) {
                const nextBuffer = this.buffers[0];
                if (required > nextBuffer.length) {
                    partialChunks.push(nextBuffer);
                    required -= nextBuffer.length;
                    this.buffers.shift();
                } else {
                    const partialBuffer = currentBuffer.subarray(0, required);
                    partialChunks.push(partialBuffer);
                    this.offset = partialBuffer.length;
                    required -= partialBuffer.length;
                }
            }
            const chunk = new Float32Array(n);
            let resultOffset = 0;
            for (const _chunk of partialChunks) {
                chunk.set(_chunk, resultOffset);
                resultOffset += _chunk.length;
            }
            return chunk;
        }
    }
    writeAudioData(buffer: Float32Array) {
        this.length += buffer.length;
        this.buffers.push(buffer);
    }
    available(size: number) {
        return this.length >= size;
    }
    size() {
        return this.length;
    }
    clean() {
        this.buffers = [];
        this.offset = 0;
        this.length = 0;
    }
}
