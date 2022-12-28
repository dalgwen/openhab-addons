import { MediaSessionCtrl, PlaybackState } from "../../stores/media-players/media-session";
import { SpotifyPlatformCtrl } from "../platform";

export class ElectronSpotifyCtrl implements SpotifyPlatformCtrl {
    async initSpotify(): Promise<void> {
        // TODO:
    }
    async activateSpotify(): Promise<void> {
        // nothing to do
    }
    async getPlayer(): Promise<MediaSessionCtrl | undefined> {
        return;
    }
    async setToken(token: string): Promise<void> {

    }
    async initPlayer(name: string): Promise<void> {

    }
    async isEnabled(): Promise<boolean> {
        return false;
    }
    playOnThisDevice(mediaUri: string): Promise<void> {
        throw new Error("Method not implemented.");
    }
    connect(): Promise<boolean> {
        throw new Error("Method not implemented.");
    }
    disconnect(): Promise<void> {
        throw new Error("Method not implemented.");
    }
    setPlaybackStateListener(listener: (state: PlaybackState, songImage: string, songTile: string) => void): Promise<void> {
        throw new Error("Method not implemented.");
    }

}