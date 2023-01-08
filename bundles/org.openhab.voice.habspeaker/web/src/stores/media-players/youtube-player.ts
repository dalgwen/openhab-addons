import { ref } from "vue";
import { defineStore, storeToRefs } from "pinia";
import { MediaProvider, MediaTarget, PlaybackState, useMediaSessionStore } from "./media-session";
export const useYoutubePlayerStore = defineStore("youtube", () => {
  const mediaSessionStore = useMediaSessionStore();
  const { mediaController, mediaState } = storeToRefs(mediaSessionStore);
  const player = ref<YT.Player | null>(null);
  let playlistId: string | undefined;
  let desiredPlaylistIndex: number | undefined;
  let desiredSecond: number | undefined;
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
            console.debug("youtube api loaded!");
            delete (window as any).onYouTubeIframeAPIReady;
            resolve();
          };
          firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);
        } else {
          resolve();
        }
      } catch (error) {
        reject(error);
      }
    });
  }
  async function playVideo(media: MediaTarget) {
    await loadYoutubeApi();
    const Player = window.YT.Player;
    if (player.value) {
      if (media.playlistId) {
        playlistId = media.playlistId;
        // we are assuming the playlist index is correct which maybe is not right, can be improved by checking the mediaId matches after load.
        player.value.loadPlaylist({ listType: "playlist", list: playlistId, index: media.playlistIndex, startSeconds: media.startSecond });
      } else if (media.mediaId) {
        playlistId = undefined;
        player.value.loadVideoById({ videoId: media.mediaId, startSeconds: media.startSecond });
      }
      return;
    }
    const playerVars: YT.PlayerVars = {
      modestbranding: 1,
      playsinline: 1,
      autoplay: 1,
      autohide: 1,
      enablejsapi: 1,
    };
    let videoId: string | undefined;
    if (media.playlistId) {
      playlistId = media.playlistId;
      playerVars.listType = 'playlist';
      playerVars.list = playlistId;
      if (media.playlistIndex != null && media.playlistIndex !== 0) {
        // Youtube iframe do not expose a way to load a playlist index we need to fake this
        desiredPlaylistIndex = media.playlistIndex;
        desiredSecond = media.startSecond;
      } else {
        desiredPlaylistIndex = undefined;
        desiredSecond = undefined;
        if (media.startSecond != null) {
          playerVars.start = media.startSecond;
        }
      }
    } else {
      playlistId = undefined;
      videoId = media.mediaId;
      if (media.startSecond != null) {
        playerVars.start = media.startSecond;
      }
    }
    const playerOptions: YT.PlayerOptions = {
      height: '0', // iframe height/width is forced by the global styles
      width: '0',
      playerVars,
      events: {
        'onReady': onPlayerReady,
        'onStateChange': onPlayerStateChange
      },
    };
    if (videoId != null) {
      playerOptions.videoId = videoId;
    }
    player.value = new Player('youtube-player', playerOptions);
  }
  const onPlayerReady: YT.PlayerEventHandler<YT.PlayerEvent> = function (event) {
    const player = event.target;
    if (desiredPlaylistIndex && playlistId) {
      player.loadPlaylist({ listType: "playlist", list: playlistId, index: desiredPlaylistIndex, startSeconds: desiredSecond });
    }
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
      async getPlaylistId() {
        return playlistId;
      },
      async getPlaylistIndex() {
        return playlistId ? player.getPlaylistIndex() : undefined;
      },
      play: async () => player.playVideo(),
      pause: async () => player.pauseVideo(),
      stop: async () => player.stopVideo(),
      next: async () => player.nextVideo(),
      previous: async () => player.previousVideo(),
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
      player.value = null;
      playerRef.destroy();
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