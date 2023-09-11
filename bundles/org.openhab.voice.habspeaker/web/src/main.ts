import "./assets/main.css";
import microphoneIconSvg from "./assets/microphone.svg?raw";
import speakerIconSvg from "./assets/speaker.svg?raw";
import { platform } from './platforms';
import { HABSpeakerREST, NotFoundError, UIConfig, UnauthorizedError } from './api';
import { IOEventListeners, IOMain } from './utils/io-main';
import { ScreenSaverManager } from './utils/screen-saver-manager';
import { MediaPlayerManager, MediaProvider } from "./media/media-player";
import { MediaCommandCmd } from "./utils/io-types";
const restAPI = new HABSpeakerREST(platform.getSpeakerId.bind(platform), platform.getUrlOpenHAB.bind(platform));
(async () => {
    console.log("Setting up HABSpeaker UI");
    const appRoot = queryElement<HTMLDivElement>("#app_root");
    const configPanelTemplate = queryElement<HTMLTemplateElement>("#local_config_template");
    const logoElement = queryElement<HTMLImageElement>("#oh-logo");
    logoElement.addEventListener("click", () => openHiddenOptions(appRoot, configPanelTemplate));
    const serverToken = await platform.getServerToken();
    const allowLoginRedirect = !await platform.isServerTokenNeeded();
    if ((!allowLoginRedirect && !serverToken) || !await platform.getSpeakerId()) {
        return openLocalConfigPanel(appRoot, configPanelTemplate, "Configuration needed", () => initializeSpeaker(appRoot, configPanelTemplate, allowLoginRedirect));
    } else {
        return initializeSpeaker(appRoot, configPanelTemplate, allowLoginRedirect);
    }
})().catch(err => {
    console.error("Initialization error:", err)
});

async function initializeSpeaker(appRoot: HTMLDivElement, configPanelTemplate: HTMLTemplateElement, allowLoginRedirect: boolean): Promise<void> {
    const speakerId = await platform.getSpeakerId();
    const retry = () => initializeSpeaker(appRoot, configPanelTemplate, allowLoginRedirect);
    if (!speakerId) {
        return openLocalConfigPanel(appRoot, configPanelTemplate, "Speaker id required.", retry);
    }
    restAPI.setServerToken(await platform.getServerToken());
    let uiConfig: UIConfig | null = null;
    try {
        uiConfig = await restAPI.getUIConfig();
    } catch (error) {
        if (error instanceof UnauthorizedError) {
            console.debug("Authorization required!");
            if (await platform.isServerTokenNeeded()) {
                return openLocalConfigPanel(appRoot, configPanelTemplate, "Token no valid!", retry);
            }
            if (allowLoginRedirect) {
                await restAPI.authorize();
                await initializeSpeaker(appRoot, configPanelTemplate, false);
            } else {
                throw error;
            }
        } else if (error instanceof NotFoundError) {
            console.debug("Unregistered speaker");
        } else {
            throw error;
        }
    }
    const screenSaverTemplate = queryElement<HTMLTemplateElement>("#screen_saver_template");
    const mediaPlayer = new MediaPlayerManager(appRoot);
    const screenSaver = new ScreenSaverManager(getScreenSaverCallback(appRoot, screenSaverTemplate), () => mediaPlayer.getPlayer()?.getAwakeScreen() ?? false);
    screenSaver.setSeconds(300);
    screenSaver.bindUserEvents();
    const speakerWidget = queryElement<HTMLButtonElement>("#speaker_widget");
    const ioListeners = getIOListeners(
        screenSaver,
        mediaPlayer,
        {
            speakerWidget,
            speakerWidgetIcon: queryElement("#speaker_icon"),
        }
    );
    const ioMain = new IOMain(await platform.getUrlOpenHAB(), ioListeners);
    speakerWidget.addEventListener("click", ioMain.sendSpot.bind(ioMain));
    restAPI.setTokenListener(accessToken => ioMain.setAuthToken(accessToken));
    platform.setup(async () => {
        await ioMain.initialize(speakerId, restAPI.getAccessToken());
    }).catch(err => console.error("Platform setup has failed:", err));
}
type ElementRefs = {
    speakerWidget: HTMLButtonElement;
    speakerWidgetIcon: HTMLAnchorElement;
};
function getIOListeners(screenSaver: ScreenSaverManager, mediaPlayer: MediaPlayerManager, elementRefs: ElementRefs): IOEventListeners {
    const { speakerWidget, speakerWidgetIcon } = elementRefs;
    let online = false;
    let listening = false;
    let speaking = false;
    const updateSpeakerIcon = () => {
        if (online) {
            if (speaking) {
                speakerWidgetIcon.innerHTML = speakerIconSvg;
            } else if (listening) {
                speakerWidgetIcon.innerHTML = microphoneIconSvg;
            } else {
                speakerWidgetIcon.innerHTML = "";
            }
        }
    };
    return {
        onMediaCommand: getMediaCommandHandler(mediaPlayer),
        onConnected() {
            screenSaver.awake();
            speakerWidget.disabled = false;
            online = true;
            updateSpeakerIcon();
        },
        onDisconnected() {
            screenSaver.awake();
            speakerWidget.disabled = true;
            online = true;
            updateSpeakerIcon();
        },
        onConfigured(config) {
            screenSaver.awake();
            const { screenSaverTime, dimScreen, keepAwake } = config;
            if (screenSaverTime != null && !isNaN(screenSaverTime)) {
                screenSaver.setSeconds(screenSaverTime);
            }
            dimScreenEnabled = !!dimScreen;
            platform.keepDeviceAwake(!!keepAwake);
        },
        onStartListening() {
            listening = true;
            mediaPlayer.muteMediaVolume(true).catch(err => console.error("Error dimming media volume:", err));
            updateSpeakerIcon();
            screenSaver.awake();
        },
        onStopListening() {
            listening = false;
            mediaPlayer.muteMediaVolume(false).catch(err => console.error("Error dimming media volume:", err));
            updateSpeakerIcon();
            screenSaver.awake();
        },
        onStartSpeaking() {
            speaking = true;
            mediaPlayer.muteMediaVolume(true).catch(err => console.error("Error dimming media volume:", err));
            updateSpeakerIcon();
            screenSaver.awake();
        },
        onStopSpeaking() {
            speaking = false;
            mediaPlayer.muteMediaVolume(false).catch(err => console.error("Error dimming media volume:", err));
            updateSpeakerIcon();
            screenSaver.awake();
        },
    };
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

// screen saver
let dimScreenEnabled: boolean = false;
function getScreenSaverCallback(appRoot: HTMLDivElement, screenSaverTemplate: HTMLTemplateElement) {
    let screenSaverClone: Node | null = null;
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
                const div = queryElement("div", screenSaverTemplate.content);
                screenSaverClone = div.cloneNode(true);
                appRoot.appendChild(screenSaverClone);
                const logo = queryElement<HTMLImageElement>("#screen_saver_logo", appRoot);
                iconMovementInterval = setInterval(getMoveIconFn(logo), 5000);
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
let clickCounter = 0;
let clickTime = 0;
async function openHiddenOptions(appRoot: HTMLDivElement, configPanelTemplate: HTMLTemplateElement) {
    const time = new Date().getTime();
    if (time - 3000 < clickTime) {
        clickCounter++
    } else {
        clickCounter = 1;
    }
    clickTime = time;
    if (clickCounter >= 5) {
        openLocalConfigPanel(appRoot, configPanelTemplate, "Hidden menu", () => Promise.resolve(location.reload()))
    }
}
let formContainerNode: Node | null = null;
async function openLocalConfigPanel(appRoot: HTMLDivElement, configPanelTemplate: HTMLTemplateElement, message: string, cb: () => Promise<void>) {
    const formContainer = queryElement<HTMLDivElement>("#config_form_container", configPanelTemplate.content).cloneNode(true);
    formContainerNode = formContainer;
    appRoot.appendChild(formContainer);
    const p = queryElement<HTMLParagraphElement>("#config_form_msg", appRoot);
    p.textContent = message;
    queryElement<HTMLInputElement>('#config_form_container input[name="speaker-id"]').value = await platform.getSpeakerId() ?? ""
    const serverOptions = queryElement<HTMLParagraphElement>("#form_server_options", appRoot);
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
        if (formContainerNode) {
            appRoot.removeChild(formContainerNode);
            formContainerNode = null;
        }
        await platform.setSpeakerSettings({
            speakerId: value["speaker-id"],
            ohUrl: value["oh-url"],
            ohToken: value["oh-token"],
        });
        await cb();
    });
}

// utils 
function queryElement<T extends Element>(selector: string, parent?: HTMLElement | DocumentFragment) {
    const el = (parent ?? document).querySelector<T>(selector);
    if (el == null) {
        throw new Error("Missing required element: " + selector);
    }
    return el;
}