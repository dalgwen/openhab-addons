import { ref, watch } from "vue";
import { defineStore, storeToRefs } from "pinia";
import { MediaProvider, PlaybackState, useMediaSessionStore } from "./media-players/media-session";
import { useSpotifyPlayerStore } from "./media-players/spotify-player";
import { useIOStore } from "./io";
export const useAssistantStore = defineStore("assistant", () => {
  const ioStore = useIOStore();
  const spotifyStore = useSpotifyPlayerStore();
  const { mediaController, mediaState } = storeToRefs(useMediaSessionStore());
  // state
  const miniMode = ref(false);
  const userInteractionDone = ref(false);
  const mediaProvider = ref("");
  const mediaId = ref("");
  function updateProvider(provider: string, media: string) {
    mediaProvider.value = provider;
    mediaId.value = media;
    miniMode.value = true;
  }
  function startMedia(provider: string, media: string) {
    if (mediaStateUpdateInterval) {
      clearInterval(mediaStateUpdateInterval);
      mediaStateUpdateInterval = null;
    }
    console.debug(`starting media ${provider}: ${media}`)
    switch (provider) {
      case MediaProvider.WEB_AUDIO:
      case MediaProvider.WEB_VIDEO:
      case MediaProvider.YOUTUBE:
        if (mediaId.value == media && mediaProvider.value == provider && mediaController.value) {
          // if media is the same we should force a reload
          const mediaCtrl = mediaController.value;
          mediaCtrl.seek(0)
            .then(() => mediaCtrl.play())
            .catch((err) => console.error("Error reloading media: ", err));
          // TODO: restart the media state interval is needed?
        } else {
          updateProvider(provider, media);
        }
        break;
      case 'spotify':
        if (spotifyStore.isConnected()) {
          spotifyStore.playUri(media);
        }
        break;
      default:
        console.error('unsupported media provider ', provider);
        return;
    }
    miniMode.value = true;
  }
  function stopMedia() {
    mediaProvider.value = "";
    mediaId.value = "";
    miniMode.value = false;
  }
  async function startAssistant(id: string, token: string) {
    userInteractionDone.value = true;
    await spotifyStore.activatePlayer();
    await ioStore.init(id, token);
  }
  // media control
  async function updateMediaState() {
    const player = mediaController.value;
    if (player == null) {
      ioStore.sendMediaState({
        totalSeconds: 0,
        currentSecond: 0,
        state: PlaybackState.STOPPED,
        volume: 0,
        provider: "",
        id: "",
      });
    } else {
      ioStore.sendMediaState({
        totalSeconds: Math.floor(await player.getTotalSeconds()),
        currentSecond: Math.floor(await player.getCurrentSecond()),
        volume: Math.floor(await player.getVolume()),
        state: await player.getPlaybackState(),
        provider: player.getId(),
        id: await player.getMediaId(),
      });
    }
  }
  let mediaStateUpdateInterval: any = null;
  watch(mediaState, (value) => {
    if (value == PlaybackState.PLAYING) {
      // TODO: made interval configurable
      if (mediaStateUpdateInterval == null) {
        mediaStateUpdateInterval = setInterval(updateMediaState, 10000);
      }
    } else {
      if (mediaStateUpdateInterval) {
        clearInterval(mediaStateUpdateInterval);
        mediaStateUpdateInterval = null;
      }
    }
    updateMediaState();
  });
  // component actions
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
    mediaProvider,
    mediaId,
    userInteractionDone,
    startListening,
    startAssistant,
    resetConnection,
    isAudioSupported,
    updateProvider,
    startMedia,
    stopMedia,
  };
});
