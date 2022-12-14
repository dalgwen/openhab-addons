import { ref, watch } from "vue";
import { defineStore, storeToRefs } from "pinia";
import { MediaProvider, startWebsocketWorker } from "../utils/websocket-manager";
import { WorkerInCmd } from "../utils/websocket-worker";
import { useScreenSaverStore } from "./screen-saver";
import { PlaybackState, useMediaSessionStore } from "./media-players/media-session";
import { useSpotifyPlayerStore } from "./media-players/spotify-player";
export const useAssistantStore = defineStore("assistant", () => {
  const spotifyStore = useSpotifyPlayerStore();
  const { mediaController, mediaState } = storeToRefs(useMediaSessionStore());
  const { awakeScreenSaver, setScreenSaverTime } = useScreenSaverStore();
  // state
  const listening = ref(false);
  const miniMode = ref(false);
  const speaking = ref(false);
  const online = ref(false);
  const mediaProvider = ref("");
  const mediaId = ref("");
  const userInteractionDone = ref(false);
  // worker actions
  function setListening(value: boolean) {
    awakeScreenSaver();
    listening.value = value;
  }
  function setSpeaking(value: boolean) {
    awakeScreenSaver();
    speaking.value = value;
  }
  function setOnline(value: boolean) {
    awakeScreenSaver();
    if (spotifyStore.isEnabled()) {
      console.log("Starting spotify");
      if (value) {
        spotifyStore.connect()
          .then((connected) => console.debug("Spotify is connected: " + connected))
          .catch(() => console.error("Error connecting to spotify"));
      } else if (!value) {
        spotifyStore.disconnect()
          .then(() => console.debug("Spotify is disconnected"))
          .catch(() => console.error("Error connecting to spotify"));
      }
    }
    if (!value) {
      stopMedia();
    }
    online.value = value;
  }
  function getMediaCtrl() {
    return mediaController.value
  }
  function updateSpotifyToken(token: string) {
    spotifyStore.updateToken(token);
  }
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
  // worker setup
  let worker: Worker | null = null;
  function postToWorker(cmd: string, args: { [key: string]: any } = {}) {
    try {
      if (worker) {
        worker.postMessage({ cmd, ...args });
      } else {
        console.error("Worker not running");
      }
    } catch (error) {
      console.error("Unable to post to worker", error);
    }
  }
  function startWorker(id: string, token: string) {
    userInteractionDone.value = true;
    spotifyStore.activatePlayer();
    if (!worker) {
      return startWebsocketWorker(id, token, {
        getMediaCtrl,
        setListening,
        setOnline,
        setScreenSaverTime,
        setSpeaking,
        startMedia,
        stopMedia,
        updateSpotifyToken,
      }).then(async (_worker: any) => {
        worker = _worker;
        console.info("worker running");
        return worker;
      });
    }
    return Promise.resolve(worker);
  }
  // media control
  async function updateMediaState() {
    const player = mediaController.value;
    if (player == null) {
      postToWorker(WorkerInCmd.MEDIA_STATE, {
        totalSeconds: 0,
        currentSecond: 0,
        state: PlaybackState.STOPPED,
        volume: 0,
        provider: "",
        id: "",
      });
    } else {
      postToWorker(WorkerInCmd.MEDIA_STATE, {
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
    postToWorker(WorkerInCmd.ON_SPOT);
  }
  function resetConnection(id: string) {
    postToWorker(WorkerInCmd.RESET_CONNECTION, { id });
  }
  function renewToken(token: string) {
    if (worker) {
      postToWorker(WorkerInCmd.TOKEN_RENEW, { token });
    }
  }
  function isAudioSupported() {
    const getUserMediaSupported =
      window.navigator &&
      window.navigator.mediaDevices &&
      window.navigator.mediaDevices.getUserMedia;
    return AudioContext && getUserMediaSupported;
  }
  return {
    listening,
    online,
    speaking,
    miniMode,
    mediaProvider,
    mediaId,
    startListening,
    startWorker,
    userInteractionDone,
    resetConnection,
    renewToken,
    isAudioSupported,
    updateProvider,
    startMedia,
    stopMedia,
  };
});
