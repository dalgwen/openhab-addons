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
  async function claimPlayback() {
    await spotifyPlatformCtrl?.claimPlayback();
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
  async function playbackListener(playbackState: PlaybackState, _songImage: string, _songName: string) {
    if(playbackState == PlaybackState.STOPPED) {
      if(mediaController.value && mediaController.value.getId() == MediaProvider.SPOTIFY) {
        mediaSessionStore.stopMedia();
      }
      return;
    }
    songName.value = _songName;
    songImg.value = _songImage;
    mediaState.value = playbackState;
    if (playbackState == PlaybackState.PLAYING) {
      const player = await spotifyPlatformCtrl?.getPlayer();
      if (player) {
        mediaController.value = player;
      } else {
        console.warn("Unable to load Spotify controller");
      }
      mediaSessionStore.updateProvider("spotify");
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
    claimPlayback,
    updateToken,
  };
});
