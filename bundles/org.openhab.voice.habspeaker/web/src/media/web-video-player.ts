import { MediaPlayerFactory, MediaProvider, PlaybackState, PlayerCtrl } from "./media-player";
import { getPlayer } from "./web-audio-player";
export class WebVideoPlayerFactory implements MediaPlayerFactory {
  private videoElement?: HTMLVideoElement;
  constructor(private root: HTMLElement) { }
  getId(): MediaProvider {
    return MediaProvider.VIDEO_PLAYER;
  }
  claim(): Promise<boolean> {
    return Promise.resolve(false);
  }
  async getPlayer(mediaId: string, setMediaState: (state: PlaybackState) => void): Promise<PlayerCtrl> {
    let createNew = false;
    if (!this.videoElement) {
      createNew = true;
      this.videoElement = document.createElement("video");
      this.videoElement.controls = true;
      this.videoElement.autoplay = true;
      this.videoElement.preload = "auto";
      this.videoElement.src = mediaId;
    } else {
      this.videoElement.src = mediaId;
    }
    const videoElement = this.videoElement;
    return new Promise(resolve => {
      videoElement.addEventListener("load", () => {
        const player = getPlayer(this.getId(), videoElement, setMediaState, true);
        resolve(player);
      });
      if (createNew) {
        this.root.appendChild(videoElement);
      } else {
        videoElement.load();
      }
    })
  }
  async killPlayer(): Promise<void> {
    this.videoElement?.remove();
    this.videoElement = undefined;
  }

}