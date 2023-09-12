import "./assets/main.css";
import microphoneIconSvg from "./assets/microphone.svg?raw";
import openHABLogoURL from "./assets/openhab-logo.svg?url";
import speakerIconSvg from "./assets/speaker.svg?raw";
import { platform } from './platforms';
import { HABSpeakerREST, UIConfig, UnauthorizedError } from './api';
import { IOEventListeners, IOMain } from './utils/io-main';
import { ScreenSaverManager } from './utils/screen-saver-manager';
import { MediaPlayerManager, MediaProvider } from "./media/media-player";
import { MediaCommandCmd } from "./utils/io-types";
import { ReentrantLock } from "reentrant-lock";
const restAPI = new HABSpeakerREST(platform.getSpeakerId.bind(platform), platform.getUrlOpenHAB.bind(platform));

window.addEventListener('load', () => {
    const appRoot = queryElement<HTMLDivElement>("#app_root");
    const speakerWidget = queryElement<HTMLButtonElement>("#speaker_widget");
    const optionsForm = new OptionsFormCtrl(appRoot, queryElement("#local_config_template"));
    const tooltip = new TooltipCtrl(appRoot, queryElement("#tooltip_template"));
    const iconCtrl = new IconCtrl(speakerWidget, queryElement("#speaker_icon", speakerWidget));
    const mediaPlayer = new MediaPlayerManager(appRoot);
    const themeCtrl = new ThemeCtrl(queryElement("#oh-logo"), queryElement("#speaker_label"));
    const screenSaver = new ScreenSaverManager(getScreenSaverCallback(appRoot, queryElement("#screen_saver_template"), themeCtrl), () => mediaPlayer.getPlayer()?.getAwakeScreen() ?? false);
    const uiCtrl: UIControls = {
        iconCtrl,
        mediaPlayer,
        screenSaver,
        tooltip,
        optionsForm,
        themeCtrl,
    };
    (async () => {
        console.log("Setting up HABSpeaker UI");
        queryElement<HTMLImageElement>("#oh-logo").addEventListener("click", () => openHiddenOptions(optionsForm));
        const serverToken = await platform.getServerToken();
        const allowLoginRedirect = !await platform.isServerTokenNeeded();
        if ((!allowLoginRedirect && !serverToken) || !await platform.getSpeakerId()) {
            return optionsForm.open("Configuration needed", () => initializeSpeaker(uiCtrl, allowLoginRedirect));
        } else {
            return initializeSpeaker(uiCtrl, allowLoginRedirect);
        }
    })().catch(err => {
        console.error("Initialization error:", err)
        tooltip.display("Initialization failed, restart the speaker", "error");
    });
}, { once: true });
type UIControls = {
    mediaPlayer: MediaPlayerManager,
    screenSaver: ScreenSaverManager,
    iconCtrl: IconCtrl,
    optionsForm: OptionsFormCtrl,
    tooltip: TooltipCtrl,
    themeCtrl: ThemeCtrl,
}
async function initializeSpeaker(uiControls: UIControls, allowLoginRedirect: boolean): Promise<void> {
    const speakerId = await platform.getSpeakerId();
    const retry = () => initializeSpeaker(uiControls, allowLoginRedirect);
    if (!speakerId) {
        return uiControls.optionsForm.open("Speaker id required.", retry);
    }
    restAPI.setServerToken(await platform.getServerToken());
    let uiConfig: UIConfig;
    try {
        uiConfig = await restAPI.getUIConfig();
    } catch (error) {
        if (error instanceof UnauthorizedError) {
            console.debug("Authorization required!");
            if (await platform.isServerTokenNeeded()) {
                return uiControls.optionsForm.open("Token no valid!", retry);
            }
            if (allowLoginRedirect) {
                await restAPI.authorize();
                return await initializeSpeaker(uiControls, false);
            } else {
                throw error;
            }
        } else {
            throw error;
        }
    }
    uiControls.themeCtrl.setLabel(uiConfig.label);
    uiControls.themeCtrl.update(uiConfig);
    uiControls.screenSaver.setSeconds(300);
    uiControls.screenSaver.bindUserEvents();
    const ioListeners = getIOListeners(
        uiControls.screenSaver,
        uiControls.mediaPlayer,
        uiControls.iconCtrl,
        uiControls.tooltip,
        uiControls.themeCtrl,
    );
    const ioMain = new IOMain(await platform.getUrlOpenHAB(), ioListeners);
    uiControls.iconCtrl.setOnClick(ioMain.sendSpot.bind(ioMain));
    restAPI.setTokenListener(accessToken => ioMain.setAuthToken(accessToken));
    platform.setup(async () => {
        await ioMain.initialize(speakerId, restAPI.getAccessToken(), uiConfig.sampleRate);
    })
        .then((msg) => {
            if (msg) {
                uiControls.tooltip.display(msg, "info", 3000);
            }
        })
        .catch(err => console.error("Platform setup has failed:", err));
}
function getIOListeners(screenSaver: ScreenSaverManager, mediaPlayer: MediaPlayerManager, icon: IconCtrl, tooltip: TooltipCtrl, themeCtrl: ThemeCtrl): IOEventListeners {
    return {
        onMediaCommand: getMediaCommandHandler(mediaPlayer),
        onMessage: tooltip.display.bind(tooltip),
        onConnected() {
            screenSaver.awake();
            icon.setOnline(true);
        },
        onDisconnected() {
            screenSaver.awake();
            icon.setOnline(false);
        },
        onConfigured(config) {
            screenSaver.awake();
            const { screenSaverTime, dimScreen, keepAwake, label } = config;
            if (screenSaverTime != null && !isNaN(screenSaverTime)) {
                screenSaver.setSeconds(screenSaverTime);
            }
            dimScreenEnabled = !!dimScreen;
            platform.keepDeviceAwake(!!keepAwake);
            if (label?.length) {
                themeCtrl.setLabel(label);
            }
            themeCtrl.update(config);
        },
        onStartListening() {
            mediaPlayer.muteMediaVolume(true).catch(err => console.error("Error dimming media volume:", err));
            screenSaver.awake();
            icon.setListening(true);
        },
        onStopListening() {
            mediaPlayer.muteMediaVolume(false).catch(err => console.error("Error dimming media volume:", err));
            screenSaver.awake();
            icon.setListening(false);
        },
        onStartSpeaking() {
            mediaPlayer.muteMediaVolume(true).catch(err => console.error("Error dimming media volume:", err));
            screenSaver.awake();
            icon.setSpeaking(true);
        },
        onStopSpeaking() {
            mediaPlayer.muteMediaVolume(false).catch(err => console.error("Error dimming media volume:", err));
            screenSaver.awake();
            icon.setSpeaking(false);
        },
    };
}
class ThemeCtrl {
    private logoUrl = openHABLogoURL;
    constructor(private logo: HTMLImageElement, private label: HTMLSpanElement) { }
    getLogoUrl() {
        return this.logoUrl;
    }
    setLabel(value: string) {
        this.label.textContent = value;
    }
    update(theme: { primaryColor?: string, secondaryColor?: string, tertiaryColor?: string, logoUrl?: string }) {
        if (theme.logoUrl) {
            this.logoUrl = theme.logoUrl;
        } else {
            this.logoUrl = openHABLogoURL;
        }
        this.logo.src = this.logoUrl;
        document.documentElement.style.setProperty('--color-primary', theme.primaryColor ?? 'var(--oh-red)');
        document.documentElement.style.setProperty('--color-secondary', theme.secondaryColor ?? 'var(--oh-grey)');
        document.documentElement.style.setProperty('--color-tertiary', theme.tertiaryColor ?? 'var(--hs-black)');
    }
}
// icon ctrl
class IconCtrl {
    private online = false;
    private listening = false;
    private speaking = false;
    private onClick?: () => void;
    constructor(private button: HTMLButtonElement, private iconAnchor: HTMLAnchorElement) {
        button.addEventListener("click", () => this.online && this.onClick?.())
    }
    setOnline(value: boolean) {
        this.online = value;
        this.updateIcon();
    }
    setSpeaking(value: boolean) {
        this.speaking = value;
        this.updateIcon();
    }
    setListening(value: boolean) {
        this.listening = value;
        this.updateIcon();
    }
    updateIcon() {
        if (this.online) {
            this.button.disabled = false;
            if (this.speaking) {
                this.iconAnchor.innerHTML = speakerIconSvg;
            } else if (this.listening) {
                this.iconAnchor.innerHTML = microphoneIconSvg;
            } else {
                this.iconAnchor.innerHTML = "";
            }
        } else {
            this.button.disabled = true;
            this.iconAnchor.innerHTML = "";
        }
    }
    setOnClick(cb: () => void) {
        this.onClick = cb
    }
}
// media
function getMediaCommandHandler(playerMng: MediaPlayerManager) {
    return async (cmd: MediaCommandCmd) => {
        try {
            console.debug("main: running media command" + cmd.type);
            if ('start' === cmd.type) {
                playerMng.loadMedia(cmd.provider as MediaProvider, {
                    mediaId: cmd.mediaId,
                    startSecond: cmd.second,
                });
                return;
            }
            if ('claim' === cmd.type) {
                playerMng.claimMedia(cmd.provider as MediaProvider);
                return;
            }
            const mediaPlayer = playerMng.getPlayer();
            if (!mediaPlayer) {
                console.warn("main: media is not started");
                return;
            }
            switch (cmd.type) {
                case 'play':
                    await mediaPlayer.play();
                    break;
                case 'pause':
                    await mediaPlayer.pause();
                    break;
                case 'stop':
                    await playerMng.stopMedia();
                    break;
                case 'next':
                    await mediaPlayer.next();
                    break;
                case 'previous':
                    await mediaPlayer.previous();
                    break;
                case 'seek':
                    await mediaPlayer.seek(cmd.second);
                    break;
                case 'volume':
                    playerMng.setMediaVolume(cmd.level);
                    break;
                default:
                    console.error("Unsupported media command: ", cmd);
            }
        } catch (error) {
            console.error("Error while running media command: ", error);
        }
    }
}
// tooltip
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

        let timeoutRef: any = null;
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
// screen saver
let dimScreenEnabled: boolean = false;
function getScreenSaverCallback(appRoot: HTMLDivElement, screenSaverTemplate: HTMLTemplateElement, themeCtrl: ThemeCtrl) {
    let screenSaverClone: HTMLDivElement | null = null;
    let iconMovementInterval: any;
    function getMoveIconFn(logo: HTMLElement) {
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
    return (enabled: boolean) => {
        console.debug("Toggle screen saver: " + enabled);
        if (dimScreenEnabled) {
            // TODO: lock promise
            platform.dimDeviceScreen(enabled)
                .then(() => console.debug("Screen dimmed: " + enabled))
                .catch(err => console.error("Error setting screen brightness: ", err));
        }
        if (enabled) {
            if (!screenSaverClone) {
                screenSaverClone = queryElement("div", screenSaverTemplate.content)
                    .cloneNode(true).firstChild?.parentElement as HTMLDivElement;
                const logo = queryElement<HTMLImageElement>("#screen_saver_logo", screenSaverClone);
                logo.src = themeCtrl.getLogoUrl();
                iconMovementInterval = setInterval(getMoveIconFn(logo), 5000);
                appRoot.appendChild(screenSaverClone);
            }
        } else {
            if (screenSaverClone) {
                clearInterval(iconMovementInterval);
                appRoot.removeChild(screenSaverClone);
                screenSaverClone = null;
            }
        }
    }
}

// Local options panel
class OptionsFormCtrl {
    constructor(private appRoot: HTMLElement, private configPanelTemplate: HTMLTemplateElement) { }
    async open(message: string, cb: () => Promise<void>) {
        const formContainer = queryElement<HTMLDivElement>("#config_form_container", this.configPanelTemplate.content)
            .cloneNode(true).firstChild?.parentElement as HTMLDivElement;
        const p = queryElement<HTMLParagraphElement>("#config_form_msg", formContainer);
        p.textContent = message;
        queryElement<HTMLInputElement>('#config_form_container input[name="speaker-id"]', formContainer).value = await platform.getSpeakerId() ?? ""
        const serverOptions = queryElement<HTMLParagraphElement>("#form_server_options", formContainer);
        const serverOptionsNeeded = await platform.isServerTokenNeeded();
        if (!serverOptionsNeeded) {
            serverOptions.remove();
        } else {
            queryElement<HTMLInputElement>('input[name="oh-url"]', serverOptions).value = await platform.getSpeakerId() ?? ""
            queryElement<HTMLInputElement>('input[name="oh-token"]', serverOptions).value = await platform.getServerToken() ?? "";
        }
        formContainer.addEventListener("submit", async (ev: Event) => {
            const data = new FormData(ev.target as HTMLFormElement);
            const value = Object.fromEntries(data.entries()) as { "speaker-id": string, "oh-url"?: string, "oh-token"?: string, };
            this.appRoot.removeChild(formContainer);
            await platform.setSpeakerSettings({
                speakerId: value["speaker-id"],
                ohUrl: value["oh-url"],
                ohToken: value["oh-token"],
            });
            await cb();
        });
        this.appRoot.appendChild(formContainer);
    }
}
let clickCounter = 0;
let clickTime = 0;
async function openHiddenOptions(formController: OptionsFormCtrl) {
    const time = new Date().getTime();
    if (time - 3000 < clickTime) {
        clickCounter++
    } else {
        clickCounter = 1;
    }
    clickTime = time;
    if (clickCounter >= 5) {
        formController.open("Hidden menu", () => Promise.resolve(location.reload()))
    }
}

// utils 
function queryElement<T extends Element>(selector: string, parent?: HTMLElement | DocumentFragment) {
    const el = (parent ?? document).querySelector<T>(selector);
    if (el == null) {
        throw new Error("Missing required element: " + selector);
    }
    return el;
}