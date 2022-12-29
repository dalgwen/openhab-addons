import { ref } from "vue";
import { defineStore, storeToRefs } from "pinia";
import { MediaProvider, PlaybackState, useMediaSessionStore } from "./media-session";
import { getSpotifyCtrl, SpotifyPlatformCtrl } from "../../platforms";
export const useSpotifyPlayerStore = defineStore("spotify", () => {
  let token = "";
  let playerId = "";
  const mediaSessionStore = useMediaSessionStore();
  const { mediaController, mediaState } = storeToRefs(useMediaSessionStore());
  var songName = ref<string>("");
  var songImg = ref<string>("");
  let connected = false;
  let spotifyPlatformCtrl: SpotifyPlatformCtrl | undefined;
  getSpotifyCtrl().then((ctrl) => spotifyPlatformCtrl = ctrl);
  function getMediaCtrl() {
    return spotifyPlatformCtrl?.getPlayer();
  }
  async function connect() {
    spotifyPlatformCtrl?.setPlaybackStateListener(playbackListener);
    return spotifyPlatformCtrl?.connect() ?? false
  }
  async function initSpotify() {
    await spotifyPlatformCtrl?.initSpotify();
  }
  async function initPlayer(name: string) {
    await spotifyPlatformCtrl?.initPlayer(name);
  }
  async function playUri(mediaUri: string) {
    await spotifyPlatformCtrl?.playOnThisDevice(mediaUri);
  }
  function isConnected() {
    return connected;
  }
  async function isEnabled() {
    return spotifyPlatformCtrl?.isEnabled() ?? false;
  }
  async function disconnect() {
    return spotifyPlatformCtrl?.disconnect();
  }
  async function activatePlayer() {
    return spotifyPlatformCtrl?.activateSpotify();
  }
  function updateToken(accessToken: string) {
    spotifyPlatformCtrl?.setToken(accessToken);
  }
  async function playbackListener(playbackState: PlaybackState, uri: string, _songImage: string, _songName: string) {
    songName.value = _songName;
    songImg.value = _songImage;
    mediaState.value = playbackState;
    if (playbackState == PlaybackState.PLAYING) {
      const player = await spotifyPlatformCtrl?.getPlayer();
      mediaController.value = player ?? null;
      mediaSessionStore.updateProvider("spotify", uri ?? '');
    } else if(playbackState == PlaybackState.STOPPED && mediaController.value?.getId() == MediaProvider.SPOTIFY) {
      mediaSessionStore.stopMedia();
    }
  }
  return {
    songName,
    songImg,
    activatePlayer,
    connect,
    disconnect,
    initSpotify,
    initPlayer,
    isConnected,
    isEnabled,
    getMediaCtrl,
    playUri,
    updateToken,
  };
});
