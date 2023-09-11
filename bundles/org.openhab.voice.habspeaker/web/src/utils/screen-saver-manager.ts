export class ScreenSaverManager {
    private static USER_INPUT_EVENTS = [
        'contextmenu', 'auxclick', 'dblclick',
        'pointerup', 'touchend', 'keyup'
    ];
    private screenSaverSeconds = -1;
    private lastAwakeTime = Date.now();
    private active = false;
    private screenSaverTimeout: any = null;
    private isShown: boolean = false;
    constructor(private showCb: (show: boolean) => void, private isBlocked?: () => boolean) { }
    public readonly awake = () => {
        if (!this.active) {
            return;
        }
        if (this.isShown) {
            this.showScreenSaver(false);
            this.startScreenSaver(this.screenSaverSeconds);
        }
        this.lastAwakeTime = Date.now();
    };
    public setSeconds(seconds: number) {
        this.screenSaverSeconds = seconds;
        if (seconds > 0) {
            this.active = true;
            this.awake();
            this.startScreenSaver(this.screenSaverSeconds);
        } else {
            this.active = false;
            this.stopScreenSaver();
        }
    }
    public bindUserEvents() {
        ScreenSaverManager.USER_INPUT_EVENTS.forEach((eventName) => {
            window.addEventListener(eventName, this.awake, { capture: true });
        });
    }
    public unbindUserEvents() {
        ScreenSaverManager.USER_INPUT_EVENTS.forEach((eventName) => {
            window.removeEventListener(eventName, this.awake, { capture: true });
        });
    }
    private readonly onScreenSaverTimeout = () => {
        const elapsed = (Date.now() - this.lastAwakeTime) / 1000;
        if (elapsed >= this.screenSaverSeconds) {
            if (!this.isBlocked?.()) {
                this.screenSaverTimeout = null;
                this.showScreenSaver(true);
            } else {
                this.startScreenSaver(this.screenSaverSeconds);
            }
        } else {
            this.startScreenSaver((this.screenSaverSeconds - elapsed));
        }
    };
    private startScreenSaver(seconds: number) {
        this.screenSaverTimeout = setTimeout(this.onScreenSaverTimeout, seconds * 1000);
    }
    private stopScreenSaver() {
        if (this.isShown) this.showScreenSaver(false);
        if (this.screenSaverTimeout) clearTimeout(this.screenSaverTimeout);
    }
    private showScreenSaver(show: boolean) {
        this.isShown = show;
        this.showCb(show);
    }
}