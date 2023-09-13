import "./styles/index.css";

import { Platform, startPlatform } from './platforms';
import { HABSpeakerREST, UIConfig, UnauthorizedError } from './api';
import { IOEventListeners, IOMain } from './io/io-main';
import { MediaCtrl, MediaProvider } from "./ui-controls/media/media-ctrl";
import { MediaCommandCmd } from "./io/io-types";
import { OptionsFormCtrl } from "./ui-controls/options-form-ctrl";
import { queryElement } from "./ui-controls/utils";
import { TooltipCtrl } from "./ui-controls/tooltip-ctrl";
import { WidgetCtrl } from "./ui-controls/widget-ctrl";
import { ThemeCtrl } from "./ui-controls/theme-ctrl";
import { ScreenSaverCtrl } from "./ui-controls";


window.addEventListener('load', () => {
    const appRoot = queryElement<HTMLDivElement>("#app_root");
    const tooltip = new TooltipCtrl(appRoot, queryElement("#tooltip_template"));
    (async () => {
        const platform = await startPlatform()
        const restAPI = new HABSpeakerREST(platform.getSpeakerId.bind(platform), platform.getUrlOpenHAB.bind(platform), platform.getUrlLogin.bind(platform), await platform.redirectToRoot());
        const speakerWidget = queryElement<HTMLButtonElement>("#speaker_widget");
        const optionsForm = new OptionsFormCtrl(appRoot, queryElement("#local_config_template"), platform);
        const widget = new WidgetCtrl(speakerWidget);
        const media = new MediaCtrl(appRoot);
        const theme = new ThemeCtrl(queryElement("#oh-logo"), queryElement("#speaker_label"));
        const screenSaver = new ScreenSaverCtrl(appRoot, queryElement("#screen_saver_template"), platform, theme, media);
        const uiCtrl: UIControls = {
            widget,
            media,
            screenSaver,
            tooltip,
            optionsForm,
            theme,
        };
        console.log("Setting up HABSpeaker UI");
        queryElement<HTMLImageElement>("#oh-logo").addEventListener("click", () => openHiddenOptions(optionsForm));
        const serverToken = await platform.getServerToken();
        const allowLoginRedirect = !await platform.isServerTokenNeeded();
        if ((!allowLoginRedirect && !serverToken) || !await platform.getSpeakerId()) {
            return optionsForm.open("Configuration needed", () => initializeSpeaker(restAPI, platform, uiCtrl, allowLoginRedirect));
        } else {
            return initializeSpeaker(restAPI, platform, uiCtrl, allowLoginRedirect);
        }
    })().catch(err => {
        console.error("Initialization error:", err)
        tooltip.display("Initialization failed, restart the speaker", "error");
    });
}, { once: true });
type UIControls = {
    media: MediaCtrl,
    screenSaver: ScreenSaverCtrl,
    widget: WidgetCtrl,
    optionsForm: OptionsFormCtrl,
    tooltip: TooltipCtrl,
    theme: ThemeCtrl,
}
async function initializeSpeaker(restAPI: HABSpeakerREST, platform: Platform, uiControls: UIControls, allowLoginRedirect: boolean): Promise<void> {
    const speakerId = await platform.getSpeakerId();
    const retry = () => initializeSpeaker(restAPI, platform, uiControls, allowLoginRedirect);
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
                console.debug("Trying to get credentials...");
                await restAPI.authorize();
                return await initializeSpeaker(restAPI, platform, uiControls, false);
            } else {
                throw error;
            }
        } else {
            throw error;
        }
    }
    uiControls.theme.setLabel(uiConfig.label);
    uiControls.theme.update(uiConfig);
    const ioListeners = getIOListeners(
        platform,
        uiControls.screenSaver,
        uiControls.media,
        uiControls.widget,
        uiControls.tooltip,
        uiControls.theme,
    );
    const ioMain = new IOMain(await platform.getUrlOpenHAB(), ioListeners);
    uiControls.widget.setOnClick(ioMain.sendSpot.bind(ioMain));
    restAPI.setTokenListener(accessToken => ioMain.setAuthToken(accessToken));
    platform.setup(async () => {
        ioMain.setAuthToken(restAPI.getAccessToken());
        await ioMain.initialize(speakerId, uiConfig.sampleRate);
    })
        .then((msg) => {
            if (msg) {
                uiControls.tooltip.display(msg, "info", 3000);
            }
        })
        .catch(err => {
            console.error("Platform setup has failed:", err);
            uiControls.tooltip.display("Speaker setup has failed, please examine logs and report the issue", "info", 3000);
        });
}
function getIOListeners(platform: Platform, screenSaver: ScreenSaverCtrl, mediaPlayer: MediaCtrl, widget: WidgetCtrl, tooltip: TooltipCtrl, themeCtrl: ThemeCtrl): IOEventListeners {
    return {
        onMediaCommand: getMediaCommandHandler(mediaPlayer),
        onMessage: tooltip.display.bind(tooltip),
        onConnected() {
            screenSaver.awake();
            widget.setOnline(true);
        },
        onDisconnected() {
            screenSaver.awake();
            widget.setOnline(false);
        },
        onConfigured(config) {
            screenSaver.awake();
            const { screenSaverTime, dimScreen, keepAwake, label } = config;
            if (screenSaverTime != null && !isNaN(screenSaverTime)) {
                screenSaver.setSeconds(screenSaverTime);
            }
            screenSaver.toggleScreenDim(!!dimScreen);
            platform.keepDeviceAwake(!!keepAwake);
            if (label?.length) {
                themeCtrl.setLabel(label);
            }
            themeCtrl.update(config);
        },
        onStartListening() {
            mediaPlayer.muteMediaVolume(true).catch(err => console.error("Error dimming media volume:", err));
            screenSaver.awake();
            widget.setListening(true);
        },
        onStopListening() {
            mediaPlayer.muteMediaVolume(false).catch(err => console.error("Error dimming media volume:", err));
            screenSaver.awake();
            widget.setListening(false);
        },
        onStartSpeaking() {
            mediaPlayer.muteMediaVolume(true).catch(err => console.error("Error dimming media volume:", err));
            screenSaver.awake();
            widget.setSpeaking(true);
        },
        onStopSpeaking() {
            mediaPlayer.muteMediaVolume(false).catch(err => console.error("Error dimming media volume:", err));
            screenSaver.awake();
            widget.setSpeaking(false);
        },
    };
}
function getMediaCommandHandler(playerMng: MediaCtrl) {
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

