import { MediaPlayerFactory, MediaProvider, PlaybackState, PlayerCtrl } from "./media-ctrl";
import { getPlayer } from "./web-audio-player";
export class WebVideoPlayerFactory implements MediaPlayerFactory {
  constructor() { }
  getId(): MediaProvider {
    return MediaProvider.VIDEO_PLAYER;
  }
  claim(): Promise<boolean> {
    return Promise.resolve(false);
  }
  async getPlayer(setMediaState: (state: PlaybackState) => void): Promise<PlayerCtrl> {
    const videoElement = document.createElement("video");
    videoElement.controls = true;
    videoElement.autoplay = true;
    videoElement.classList.add("player");
    return getPlayer(this.getId(), videoElement, setMediaState, true);
  }
}