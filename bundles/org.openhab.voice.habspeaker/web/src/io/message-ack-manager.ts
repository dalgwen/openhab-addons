export class MessageACKManager {
    private activeACKs = new Map<number, MessageACK>();

    constructor(private name: string) { }

    createACK(): number {
        let ack = { code: Math.random(), } as MessageACK;
        ack.promise = new Promise((resolve, reject) => { ack.resolve = resolve; ack.reject = reject; });
        this.activeACKs.set(ack.code, ack);
        return ack.code;
    }
    confirmACK(code: number) {
        console.debug(this.name + ": got ack confirmation: " + code);
        this.activeACKs.get(code)?.resolve();
        this.activeACKs.delete(code);
    }
    abortACK(code: number) {
        console.warn(this.name + ": abort ack confirmation: " + code);
        this.activeACKs.get(code)?.reject(new Error("Aborted"));
        this.activeACKs.delete(code);
    }
    async awaitACK(code: number) {
        console.debug(this.name + ": waiting for ack confirmation: " + code);
        await this.activeACKs.get(code)?.promise;
        console.debug(this.name + ": resumed after ack confirmation: " + code);
    }

}
export interface MessageACK {
    code: number,
    promise: Promise<void>,
    resolve: () => void,
    reject: (err: unknown) => void,
}