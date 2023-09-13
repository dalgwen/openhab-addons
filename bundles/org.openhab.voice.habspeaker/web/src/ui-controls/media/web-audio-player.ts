import { MediaProvider, PlayerCtrl, PlaybackState, MediaPlayerFactory } from "./media-ctrl";

export class WebAudioPlayerFactory implements MediaPlayerFactory {
  private audioElement?: HTMLAudioElement;
  constructor(private root: HTMLElement) { }
  claim(): Promise<boolean> {
    return Promise.resolve(false);
  }
  getId(): MediaProvider {
    return MediaProvider.AUDIO_PLAYER;
  }
  async getPlayer(mediaId: string, setMediaState: (state: PlaybackState) => void): Promise<PlayerCtrl> {
    let createNew = false;
    if (!this.audioElement) {
      createNew = true;
      this.audioElement = document.createElement("audio");
      this.audioElement.controls = true;
      this.audioElement.autoplay = true;
      this.audioElement.preload = "auto";
      this.audioElement.src = mediaId;
    } else {
      this.audioElement.src = mediaId;
      this.audioElement.load();
    }
    const audioEl = this.audioElement;
    return new Promise(resolve => {
      audioEl.addEventListener("load", () => {
        const player = getPlayer(this.getId(), audioEl, setMediaState, true);
        resolve(player);
      });
      if (createNew) {
        this.root.appendChild(audioEl);
      } else {
        audioEl.load();
      }
    })
  }
  async killPlayer(): Promise<void> {
    this.audioElement?.remove();
    this.audioElement = undefined;
  }

}
export function getPlayer(provider: MediaProvider, playerRef: HTMLVideoElement | HTMLAudioElement, setMediaState: (state: PlaybackState) => void, disableScreenSaver: boolean = false): PlayerCtrl {
  playerRef.addEventListener('pause', () => {
    setMediaState(PlaybackState.PAUSED);
  });
  playerRef.addEventListener('play', () => {
    setMediaState(PlaybackState.BUFFERING);
  });
  playerRef.addEventListener('playing', () => {
    setMediaState(PlaybackState.PLAYING);
  });
  playerRef.addEventListener('ended', () => {
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
    seek: async (second) => !isLiveStreaming(playerRef) ? playerRef.fastSeek(second) : console.warn('Media is not seekable'),
    getCurrentSecond: async () => !isLiveStreaming(playerRef) ? playerRef.currentTime : 0,
    getTotalSeconds: async () => !isLiveStreaming(playerRef) ? playerRef.duration : 0,
    getPlaybackState: async () => parseAudioElementState(playerRef),
    getAwakeScreen: () => disableScreenSaver,
    getVolume: async () => playerRef.volume * 100,
    setVolume: async (value: number) => { playerRef.volume = (value / 100); },
  };
}
function isLiveStreaming(playerRef: HTMLVideoElement | HTMLAudioElement) {
  return playerRef.duration == null || isNaN(playerRef.duration) || playerRef.duration === Number.POSITIVE_INFINITY;
}
function parseAudioElementState(playerRef: HTMLVideoElement | HTMLAudioElement) {
  if (playerRef.ended === true && !isLiveStreaming(playerRef)) {
    return PlaybackState.STOPPED;
  }
  if (playerRef.paused === true) {
    return PlaybackState.PAUSED;
  }
  return PlaybackState.PLAYING;
}