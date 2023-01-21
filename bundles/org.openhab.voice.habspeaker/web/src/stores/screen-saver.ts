import { onUnmounted, ref, watch } from "vue";
import { defineStore, storeToRefs } from "pinia";
import { PlaybackState, useMediaSessionStore } from "./media-players/media-session";
import { platform } from "../platforms";
const USER_INPUT_EVENTS = [
  'click', 'contextmenu', 'auxclick', 'dblclick',
  'mouseup', 'pointerup', 'touchend', 'keyup'
];
export const useScreenSaverStore = defineStore("screenSaver", () => {
  const { mediaController, mediaState } = storeToRefs(useMediaSessionStore());
  let screenSaverTime = 120;
  let screenSaverTimeout: any = null;
  let notifyPlatform = false;
  const screenSaverEnabled = ref(false);
  function showScreenSaver(value: boolean) {
    screenSaverEnabled.value = value;
    if (notifyPlatform) {
      platform.dimDeviceScreen(value)
        .then(() => console.debug("Screen dimmed: " + value))
        .catch(err => console.error("Error setting screen brightness: ", err));
    }
  }
  function enableScreenDim(value: boolean) {
    notifyPlatform = value;
  }
  function isScreenSaverEnabled() {
    var mediaCtrl = mediaController.value;
    return screenSaverTime > 0 && (!mediaCtrl || !mediaCtrl.getAwakeScreen() || mediaState.value != PlaybackState.PLAYING);
  }
  function disableScreenSaver() {
    if (screenSaverEnabled.value) showScreenSaver(false);
    if (screenSaverTimeout) clearTimeout(screenSaverTimeout);
  }
  function awakeScreenSaver() {
    disableScreenSaver();
    if (isScreenSaverEnabled()) {
      screenSaverTimeout = setTimeout(() => {
        screenSaverTimeout = null;
        showScreenSaver(true);
      }, screenSaverTime * 1000);
    }
  }
  function setScreenSaverTime(seconds: number) {
    screenSaverTime = seconds;
    awakeScreenSaver();
  }
  USER_INPUT_EVENTS.forEach((eventName) => {
    window.addEventListener(eventName, awakeScreenSaver, { capture: true });
  });
  watch(mediaController, awakeScreenSaver);
  watch(mediaState, awakeScreenSaver);
  awakeScreenSaver();
  onUnmounted(() => {
    USER_INPUT_EVENTS.forEach((eventName) => {
      window.removeEventListener(eventName, awakeScreenSaver, { capture: true });
    });
    disableScreenSaver();
  });
  return {
    awakeScreenSaver,
    screenSaverEnabled,
    setScreenSaverTime,
    enableScreenDim,
  };
});