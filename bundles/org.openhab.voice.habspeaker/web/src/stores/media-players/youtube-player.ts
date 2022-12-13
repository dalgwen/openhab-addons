import { ref } from "vue";
import { defineStore, storeToRefs } from "pinia";
import { PlaybackState, useMediaSessionStore } from "./media-session";
import { MediaProvider } from "../../utils/websocket-manager";
export const useYoutubePlayerStore = defineStore("youtube", () => {
  const mediaSessionStore = useMediaSessionStore();
  const { mediaController, mediaState } = storeToRefs(mediaSessionStore);
  var player = ref<YT.Player | null>(null);
  function loadYoutubeApi() {
    return new Promise<void>((resolve, reject) => {
      try {
        const iFrameApiLoaded = (window as any)['yt_embedsEnableIframeSrcWithIntent'];
        if (!iFrameApiLoaded) {
          var tag = document.createElement('script');
          tag.src = "https://www.youtube.com/iframe_api";
          var firstScriptTag = document.getElementsByTagName('script')[0];
          if (!firstScriptTag || !firstScriptTag.parentNode)
            throw new Error('Unable to load youtube api');
          (window as any).onYouTubeIframeAPIReady = function () {
            console.log("youtube api loaded!");
            delete (window as any).onYouTubeIframeAPIReady;
            resolve();
          };
          firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);
        } else {
          console.log("youtube api already loaded!");
          resolve();
        }
      } catch (error) {
        reject(error);
      }
    });
  }
  async function playVideo(mediaId: string) {
    await loadYoutubeApi();
    const Player = window.YT.Player;
    if (player.value) {
      player.value.loadVideoById(mediaId);
      return;
    }
    player.value = new Player('youtube-player', {
      height: '0', // iframe height/width is forced by the global styles
      width: '0',
      videoId: mediaId,
      playerVars: {
        modestbranding: 1,
        playsinline: 1,
        autoplay: 1,
        autohide: 1,
        enablejsapi: 1,
      },
      events: {
        'onReady': onPlayerReady,
        'onStateChange': onPlayerStateChange
      },
    });
  }
  const onPlayerReady: YT.PlayerEventHandler<YT.PlayerEvent> = function (event) {
    const player = event.target;
    mediaController.value = {
      getId: () => MediaProvider.YOUTUBE,
      getMediaId: async () => {
        const playlist = player.getPlaylist();
        if (playlist?.length) {
          const currentId = playlist[player.getPlaylistIndex()];
          if (currentId) {
            return currentId;
          }
        }
        const videoUrl = player.getVideoUrl();
        if (videoUrl?.length) {
          const parts = videoUrl.split("?");
          if (parts.length == 2) {
            return parts[1].split("&").filter(p => p.startsWith("v=")).map(p => p.replace("v=", ""))[0] ?? "";
          }
        }
        return "";
      },
      play: async () => player.playVideo(),
      pause: async () => player.pauseVideo(),
      stop: async () => player.stopVideo(),
      next: async () => player.nextVideo(),
      previous: async () => player.previousVideo(),
      forward: async () => { },
      backward: async () => { },
      seek: async (second) => player.seekTo(second, true),
      getCurrentSecond: async () => player.getCurrentTime(),
      getTotalSeconds: async () => player.getDuration(),
      getPlaybackState: async () => parseYoutubePlayerState(player.getPlayerState()),
      getAwakeScreen: () => true,
      getVolume: async () => player.getVolume(),
      setVolume: async (value: number) => player.setVolume(value)
    };
  }
  const onPlayerStateChange: YT.PlayerEventHandler<YT.OnStateChangeEvent> = function (event) {
    mediaState.value = parseYoutubePlayerState(event.data);
  }
  function destroyPlayer() {
    const playerRef = player.value;
    if (playerRef) {
      playerRef.destroy();
      player.value = null;
    }
  }
  return {
    playVideo,
    destroyPlayer
  };
});
function parseYoutubePlayerState(playerState: YT.PlayerState) {
  switch (playerState) {
    case YT.PlayerState.PLAYING:
      return PlaybackState.PLAYING;
    case YT.PlayerState.PAUSED:
      return PlaybackState.PAUSED;
    case YT.PlayerState.BUFFERING:
      return PlaybackState.BUFFERING;
    case YT.PlayerState.UNSTARTED:
    case YT.PlayerState.ENDED:
    default:
      return PlaybackState.STOPPED;
  }
}