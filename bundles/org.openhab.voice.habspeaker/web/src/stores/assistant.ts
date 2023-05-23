import { ref, watch } from "vue";
import { defineStore, storeToRefs } from "pinia";
import { useMediaSessionStore } from "./media-players/media-session";
import { useIOStore } from "./io";
export const useAssistantStore = defineStore("assistant", () => {
  const ioStore = useIOStore();
  const mediaSessionStorage = useMediaSessionStore();
  const { mediaProvider } = storeToRefs(mediaSessionStorage);
  // state
  const miniMode = ref(false);
  const userInteractionDone = ref(false);
  watch(mediaProvider, (value) => miniMode.value = !!value);
  // component actions
  async function startAssistant(id: string, token: string | null) {
    userInteractionDone.value = true;
    await ioStore.init(id, token);
  }
  function startListening() {
    ioStore.sendSpot();
  }
  function resetConnection(id: string) {
    ioStore.resetConnection(id);
  }
  function isAudioSupported() {
    if (typeof AudioContext === "undefined") {
      return false;
    }
    const getUserMediaSupported =
      !!(window.navigator &&
        window.navigator.mediaDevices &&
        window.navigator.mediaDevices.getUserMedia);
    const workletSupported = "audioWorklet" in AudioContext.prototype;
    return getUserMediaSupported && workletSupported;
  }
  return {
    miniMode,
    userInteractionDone,
    startListening,
    startAssistant,
    resetConnection,
    isAudioSupported,
  };
});
