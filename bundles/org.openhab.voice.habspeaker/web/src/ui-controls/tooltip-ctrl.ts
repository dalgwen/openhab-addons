import { ReentrantLock } from "reentrant-lock";
import { queryElement } from "./utils";

export class TooltipCtrl {
    tooltipLock = new ReentrantLock();
    private level: 'info' | 'error' = 'info';
    constructor(private appRoot: HTMLDivElement, private tooltipTemplate: HTMLTemplateElement) { }
    public display(msg: string, type: 'info' | 'error', ms?: number): (() => void) {
        if (type == 'info' && this.level !== 'info') {
            console.debug("Ignoring info tooltip: " + msg);
            return () => { };
        }
        const tooltipEl = queryElement("div", this.tooltipTemplate.content)
            .cloneNode(true).firstChild?.parentElement as HTMLDivElement;
        const p = queryElement<HTMLElement>("#text", tooltipEl);
        p.textContent = msg;
        const closeIcon = queryElement<HTMLElement>("#close", tooltipEl);

        let timeoutRef: ReturnType<typeof setTimeout>;
        let releaseLock: (() => void) | null = null;
        let closed = false;
        const close = (ev?: Event) => {
            if (closed) {
                return;
            }
            closed = true;
            ev?.stopPropagation();
            clearTimeout(timeoutRef);
            tooltipEl.remove();
            releaseLock?.();
        }
        this.tooltipLock.acquire().then(release => {
            if (closed) {
                release();
                return;
            }
            releaseLock = release;
            if (ms) {
                closeIcon.remove();
                timeoutRef = setTimeout(close, ms);
            } else {
                closeIcon.addEventListener("click", close)
            }
            this.appRoot.appendChild(tooltipEl);
        });
        return close;
    }
    public setLevel(value: typeof this.level) {
        this.level = value;
    }
}