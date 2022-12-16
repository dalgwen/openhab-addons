import { ref, watch } from "vue";
import { defineStore, storeToRefs } from "pinia";
import { useMediaSessionStore } from "./media-players/media-session";
import { useSpotifyPlayerStore } from "./media-players/spotify-player";
import { useIOStore } from "./io";
export const useAssistantStore = defineStore("assistant", () => {
  const ioStore = useIOStore();
  const spotifyStore = useSpotifyPlayerStore();
  const mediaSessionStorage = useMediaSessionStore();
  const { mediaProvider } = storeToRefs(mediaSessionStorage);
  // state
  const miniMode = ref(false);
  const userInteractionDone = ref(false);
watch(mediaProvider, (value)=> miniMode.value = !!value);
// component actions
  async function startAssistant(id: string, token: string) {
    userInteractionDone.value = true;
    await spotifyStore.activatePlayer();
    await ioStore.init(id, token);
  }
  function startListening() {
    ioStore.sendSpot();
  }
  function resetConnection(id: string) {
    ioStore.resetConnection(id);
  }
  function isAudioSupported() {
    const getUserMediaSupported =
      window.navigator &&
      window.navigator.mediaDevices &&
      window.navigator.mediaDevices.getUserMedia;
    return AudioContext && getUserMediaSupported;
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
