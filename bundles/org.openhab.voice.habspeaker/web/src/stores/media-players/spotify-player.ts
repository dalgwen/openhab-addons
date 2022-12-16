import { ref } from "vue";
import { defineStore, storeToRefs } from "pinia";
import { MediaProvider, MediaSessionCtrl, PlaybackState, useMediaSessionStore } from "./media-session";
import { useAssistantStore } from "../assistant";
export const useSpotifyPlayerStore = defineStore("spotify", () => {
  let token = "";
  let playerId = "";
  const assistantStore = useAssistantStore();
  const { mediaController, mediaState } = storeToRefs(useMediaSessionStore());
  var player = ref<Spotify.Player | null>(null);
  function loadSpotifyApi() {
    return new Promise<void>((resolve, reject) => {
      try {
        if (typeof window.Spotify === 'undefined') {
          var tag = document.createElement('script');
          tag.src = "https://sdk.scdn.co/spotify-player.js";
          var firstScriptTag = document.getElementsByTagName('script')[0];
          if (!firstScriptTag || !firstScriptTag.parentNode)
            throw new Error('Unable to load spotify api');
          (window as any).onSpotifyWebPlaybackSDKReady = function () {
            console.log("spotify api loaded!");
            delete (window as any).onSpotifyWebPlaybackSDKReady;
            resolve();
          };
          firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);
        } else {
          console.log("spotify api already loaded!");
          resolve();
        }
      } catch (error) {
        reject(error);
      }
    });
  }
  let _mediaController: MediaSessionCtrl | null = null;
  function getMediaCtrl() {
    return _mediaController;
  }
  async function initPlayer(name: string) {
    await loadSpotifyApi();
    const playerRef = player.value = new window.Spotify.Player({
      name,
      getOAuthToken: cb => cb(token),
    });
    playerRef.addListener('ready', ({ device_id }) => {
      playerId = device_id;
      console.log('Ready with Device ID', device_id);
    });
    playerRef.addListener('autoplay_failed', () => {
      console.log('Autoplay is not allowed by the browser autoplay rules');
    });
    playerRef.addListener('not_ready', ({ device_id }) => {
      connected = false;
      console.log('Device ID has gone offline', device_id);
    });
    playerRef.addListener('initialization_error', ({ message }) => {
      console.error(message);
    });

    playerRef.addListener('authentication_error', ({ message }) => {
      console.error(message);
    });
    playerRef.on('playback_error', ({ message }) => {
      console.error('Failed to perform playback', message);
    });
    playerRef.addListener('account_error', ({ message }) => {
      console.error(message);
    });
    const readyCallback = () => {
      playerRef.removeListener("ready", readyCallback);
      _mediaController = {
        getId: () => MediaProvider.SPOTIFY,
        getMediaId: async () => {
          const state = await playerRef.getCurrentState();
          if (state?.context.uri) {
            return state?.context.uri;
          }
          return "";
        },
        play: () => playerRef.resume(),
        pause: () => playerRef.pause(),
        stop: () => playerRef.pause(),
        next: () => playerRef.nextTrack(),
        previous: () => playerRef.previousTrack(),
        seek: (second) => playerRef.seek(second * 1000),
        getCurrentSecond: async () => {
          const position = (await playerRef.getCurrentState())?.position;
          if (position != null) {
            return position / 1000;
          }
          return 0;
        },
        getTotalSeconds: async () => {
          const duration = (await playerRef.getCurrentState())?.duration;
          if (duration != null) {
            return duration / 1000;
          }
          return 0;
        },
        getPlaybackState: async () => {
          return parseSpotifyPlayerState(await playerRef.getCurrentState());
        },
        getAwakeScreen: () => false,
        getVolume: async () => (await playerRef.getVolume()) * 100,
        setVolume: (value: number) => playerRef.setVolume(value / 100)
      };
    };
    playerRef.addListener('player_state_changed', (playbackState) => {
      const state = mediaState.value = parseSpotifyPlayerState(playbackState);
      if (state == PlaybackState.PLAYING) {
        mediaController.value = _mediaController;
        console.log(playbackState);
        assistantStore.updateProvider("spotify", playbackState.context.uri ?? '');
      }
    });
    playerRef.on('ready', readyCallback);
  }
  function playOnThisDevice(
    spotifyUri: string,
    playerInstance: Spotify.Player,
  ) {
    const { getOAuthToken } = playerInstance._options;
    getOAuthToken(access_token => {
      fetch(`https://api.spotify.com/v1/me/player/play?device_id=${playerId}`, {
        method: 'PUT',
        body: JSON.stringify(spotifyUri.startsWith("spotify:track:") ? { uris: [spotifyUri] } : { context_uri: spotifyUri }),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${access_token}`
        },
      }).then(() => console.debug("Spotify starting"))
        .catch((err) => console.error("Can not play on spotify", err));
    });
  };
  let connected = false;
  async function activatePlayer() {
    const playerRef = player.value;
    if (playerRef) {
      await playerRef.activateElement();
    }
  }
  async function connect() {
    const playerRef = player.value;
    connected = true;
    if (playerRef) {
      return connected = await playerRef.connect();
    }
    return false;
  }
  async function playUri(mediaUri: string) {
    const playerRef = player.value;
    if (playerRef) {
      playOnThisDevice(mediaUri, playerRef);
      return;
    }
  }
  function isConnected() {
    return connected;
  }
  function isEnabled() {
    return token.length && typeof window.Spotify !== 'undefined';
  }
  async function disconnect() {
    connected = false;
    const playerRef = player.value;
    if (playerRef) {
      playerRef.disconnect();
      player.value = null;
    }
  }
  function updateToken(accessToken: string) {
    token = accessToken;
  }
  return {
    activatePlayer,
    connect,
    disconnect,
    initPlayer,
    isConnected,
    isEnabled,
    getMediaCtrl,
    playUri,
    updateToken,
  };
});
function parseSpotifyPlayerState(playerState: Spotify.PlaybackState | null) {
  if (!playerState) {
    return PlaybackState.STOPPED
  }
  if (playerState.loading) {
    return PlaybackState.BUFFERING;
  }
  if (!playerState.paused) {
    return PlaybackState.PLAYING;
  }
  return PlaybackState.PAUSED;
}