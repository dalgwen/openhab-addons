import { ReentrantLock } from "reentrant-lock";
import { ThemeCtrl } from "..";
import { Platform } from "../../platforms";
import { MediaCtrl } from "../media/media-ctrl";
import { ScreenSaverManager } from "./screen-saver-manager";
import { queryElement } from "../utils";

export class ScreenSaverCtrl {
    private readonly manager: ScreenSaverManager;
    private dimScreenEnabled: boolean = false;
    private dimScreenLock = new ReentrantLock();
    private screenSaverClone?: HTMLDivElement;
    private iconMovementInterval?: ReturnType<typeof setInterval>;

    constructor(private readonly appRoot: HTMLDivElement, private readonly screenSaverTemplate: HTMLTemplateElement, private readonly platform: Platform, private readonly themeCtrl: ThemeCtrl, mediaPlayer: MediaCtrl) {
        this.manager = new ScreenSaverManager(this.display.bind(this), () => mediaPlayer.getPlayer()?.getAwakeScreen() ?? false);
        this.manager.setSeconds(300);
        this.manager.bindUserEvents();
    }
    public awake() {
        this.manager.awake();
    }
    public setSeconds(seconds: number) {
        this.manager.setSeconds(seconds);
    }
    public toggleScreenDim(enabled: boolean) {
        this.dimScreenEnabled = enabled;
    }
    private display(enabled: boolean) {
        console.debug("Toggle screen saver: " + enabled);
        if (this.dimScreenEnabled) {
            this.dimScreenLock.lock(async () => {
                this.platform.dimDeviceScreen(enabled)
                    .then(() => console.debug("Screen dimmed: " + enabled))
                    .catch(err => console.error("Error setting screen brightness: ", err));
            });
        }
        if (enabled) {
            if (!this.screenSaverClone) {
                this.screenSaverClone = queryElement("div", this.screenSaverTemplate.content)
                    .cloneNode(true).firstChild?.parentElement as HTMLDivElement;
                const logo = queryElement<HTMLImageElement>("#screen_saver_logo", this.screenSaverClone);
                logo.src = this.themeCtrl.getLogoUrl();
                this.iconMovementInterval = setInterval(this.getMoveIconFn(logo), 5000);
                this.appRoot.appendChild(this.screenSaverClone);
            }
        } else {
            if (this.screenSaverClone) {
                clearInterval(this.iconMovementInterval);
                this.screenSaverClone.remove();
                this.screenSaverClone = undefined;
            }
        }
    }
    private getMoveIconFn(logo: HTMLElement) {
        function getRandomArbitrary(min: number, max: number) {
            return Math.round((Math.random() * (max - min) + min) * 100) / 100
        }
        return () => {
            if (logo) {
                logo.style.top = getRandomArbitrary(5, 95) + "%";
                logo.style.left = getRandomArbitrary(10, 90) + "%";
            }
        }
    }
}
