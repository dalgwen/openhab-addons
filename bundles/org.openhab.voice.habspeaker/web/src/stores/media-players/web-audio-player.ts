import { ref } from "vue";
import { defineStore, storeToRefs } from "pinia";
import { PlaybackState, useMediaSessionStore } from "./media-session";
import { MediaProvider, MediaSessionCtrl } from "../../utils/websocket-manager";
export const useWebAudioPlayerStore = defineStore("web-audio", () => {
  const mediaSessionStore = useMediaSessionStore();
  const { mediaController, mediaState } = storeToRefs(mediaSessionStore);
  var player = ref<HTMLAudioElement | null>(null);
  function registerMediaController(playerRef: HTMLAudioElement) {
    player.value = playerRef;
    mediaController.value = getMediaSessionCtrl(MediaProvider.WEB_AUDIO, playerRef, (state) => mediaState.value = state);
  }
  return {
    registerMediaController,
  };
});
export function getMediaSessionCtrl(provider: MediaProvider, playerRef: HTMLVideoElement|HTMLAudioElement, setMediaState: (state: PlaybackState) => void, disableScreenSaver: boolean = false): MediaSessionCtrl {
  playerRef.addEventListener('pause', () => {
    setMediaState(PlaybackState.PAUSED);
  });
  playerRef.addEventListener('play', () => {
    setMediaState(PlaybackState.BUFFERING);
  });
  playerRef.addEventListener('playing', () => {
    setMediaState(PlaybackState.PLAYING);
  });
  playerRef.addEventListener('stalled', () => {
    setMediaState(PlaybackState.STOPPED);
  });
  return {
    getId: () => provider,
    getMediaId: async () => playerRef.currentSrc,
    play: async () => playerRef.play(),
    pause: async () => playerRef.pause(),
    stop: async () => playerRef.pause(),
    next: async () => { console.error("Method implementation pending"); },
    previous: async () => { console.error("Method implementation pending"); },
    seek: async (second) => (!isNaN(playerRef.duration)) ? playerRef.fastSeek(second) : console.warn('Media is not seekable'),
    getCurrentSecond: async () => (!isNaN(playerRef.duration)) ? playerRef.currentTime : 0,
    getTotalSeconds: async () => (!isNaN(playerRef.duration)) ? playerRef.duration : 0,
    getPlaybackState: async () => parseAudioElementState(playerRef),
    getAwakeScreen: () => disableScreenSaver,
    getVolume: async () => playerRef.volume * 100,
    setVolume: async (value: number) => { playerRef.volume = (value / 100); },
  };
}

function parseAudioElementState(playerRef: HTMLAudioElement) {
  if (isNaN(playerRef.duration)) {
    return PlaybackState.BUFFERING;
  }
  if (playerRef.paused === false) {
    return PlaybackState.PLAYING;
  }
  if (playerRef.paused === true) {
    return PlaybackState.PAUSED;
  }
  return PlaybackState.STOPPED;
}