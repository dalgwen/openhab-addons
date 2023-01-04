import { MediaProvider, MediaSessionCtrl, PlaybackState } from "../../stores/media-players/media-session";
import { SpotifyPlatformCtrl, SpotifyPlaybackListener } from "../platform";
// import { ipcRenderer } from "electron";

export class ElectronSpotifyCtrl implements SpotifyPlatformCtrl {
    label = "";
    token = "";
    playbackListener?: SpotifyPlaybackListener;
    // Cache the playback response to avoid calls when possible
    private playbackStateCache?: SpotifyApiPlaybackState;
    private playbackStateCacheTime = 0;
    async initSpotify(): Promise<void> {
        // nothing to do
        window.electronAPI.setSpotifyPlaybackListener(async (state: string) => {
            if (this.playbackListener) {
                if (state == 'play' || state == 'pause') {
                    this.playbackStateCacheTime = 0;
                    const playbackState = await this.getSpotifyPlaybackState();
                    if (playbackState && playbackState.device?.id == (await window.electronAPI.getSpotifyId())) {
                        this.playbackListener(
                            playbackState.is_playing ? PlaybackState.PLAYING : PlaybackState.PAUSED,
                            playbackState.item?.uri ?? '',
                            playbackState.item?.album.images[0].url ?? '',
                            playbackState.item?.name ?? '',
                        );
                    }
                } else if (state == 'stop') {
                    this.playbackListener(
                        PlaybackState.STOPPED,
                        "",
                        "",
                        "",
                    );
                }
            }
        });
    }
    async activateSpotify(): Promise<void> {
        // nothing to do
    }
    async setToken(token: string): Promise<void> {
        this.token = token;
        await window.electronAPI.setSpotifyToken(token);
    }
    async initPlayer(name: string): Promise<void> {
        this.label = name;
    }
    async isEnabled(): Promise<boolean> {
        return window.electronAPI.isSpotifyAvailable();
    }
    async playOnThisDevice(mediaUri: string): Promise<void> {
        await this.spotifyPlay(mediaUri);
    }
    async connect(): Promise<boolean> {
        await window.electronAPI.startSpotify(this.label);
        return true;
    }
    async disconnect(): Promise<void> {
        await window.electronAPI.stopSpotify();
    }
    async setPlaybackStateListener(listener: SpotifyPlaybackListener): Promise<void> {
        this.playbackListener = listener;
    }
    async getPlayer(): Promise<MediaSessionCtrl | undefined> {
        return {
            getId: () => MediaProvider.SPOTIFY,
            getMediaId: async () => (await this.getSpotifyPlaybackState())?.item.uri ?? '',
            play: async () => this.spotifyResume(),
            pause: async () => this.spotifyPause(),
            stop: async () => this.spotifyPause(),
            next: async () => { this.spotifyNext() },
            previous: async () => { this.spotifyPrevious() },
            seek: async (second) => this.seekSpotifyPlayback(second),
            getCurrentSecond: async () => ((await this.getSpotifyPlaybackState())?.progress_ms ?? 0) / 1000,
            getTotalSeconds: async () => ((await this.getSpotifyPlaybackState())?.item?.duration_ms ?? 0) / 1000,
            getPlaybackState: async () => (await this.getSpotifyPlaybackState())?.is_playing ? PlaybackState.PLAYING : PlaybackState.PAUSED,
            getAwakeScreen: () => false,
            getVolume: async () => (await this.getSpotifyPlaybackState())?.device.volume_percent ?? 0,
            setVolume: async (value: number) => this.setSpotifyVolume(value),
        };
    }
    async claimPlayback() {
        const deviceId = await window.electronAPI.getSpotifyId();
        if (!deviceId.length) {
            console.warn("Missing spotify id");
            return;
        }
        await fetch('https://api.spotify.com/v1/me/player', {
            method: 'PUT',
            body: JSON.stringify({ "device_ids": [deviceId] }),
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }).catch((err) => console.error("Can not play on spotify", err));
    }
    async spotifyPlay(spotifyUri: string) {
        await fetch(`https://api.spotify.com/v1/me/player/play?device_id=${await window.electronAPI.getSpotifyId()}`, {
            method: 'PUT',
            body: JSON.stringify(spotifyUri.startsWith("spotify:track:") ? { uris: [spotifyUri] } : { context_uri: spotifyUri }),
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }).catch((err) => console.error("Can not play on spotify", err));
    }
    async spotifyResume() {
        await fetch(`https://api.spotify.com/v1/me/player/play?device_id=${await window.electronAPI.getSpotifyId()}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }).catch((err) => console.error("Can not play on spotify", err));
    }
    async spotifyPause() {
        await fetch(`https://api.spotify.com/v1/me/player/pause?device_id=${await window.electronAPI.getSpotifyId()}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }).catch((err) => console.error("Can not play on spotify", err));
    }
    async spotifyNext() {
        await fetch(`https://api.spotify.com/v1/me/player/next?device_id=${await window.electronAPI.getSpotifyId()}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }).catch((err) => console.error("Can not play on spotify", err));
    }
    async spotifyPrevious() {
        await fetch(`https://api.spotify.com/v1/me/player/previous?device_id=${await window.electronAPI.getSpotifyId()}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }).catch((err) => console.error("Can not play on spotify", err));
    }

    async setSpotifyVolume(volume: number) {
        await fetch(`https://api.spotify.com/v1/me/player/volume?device_id=${await window.electronAPI.getSpotifyId()}&volume_percent=${Math.floor(volume)}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }).catch((err) => console.error("Can set volume on spotify", err));
    }
    async seekSpotifyPlayback(second: number) {
        await fetch(`https://api.spotify.com/v1/me/player/seek?device_id=${await window.electronAPI.getSpotifyId()}&position_ms=${Math.floor(second * 1000)}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }).catch((err) => console.error("Can set volume on spotify", err));
    }
    async getSpotifyPlaybackState(): Promise<SpotifyApiPlaybackState | undefined> {
        if (this.playbackStateCache && Date.now() < this.playbackStateCacheTime) {
            return this.playbackStateCache;
        }
        return await fetch(`https://api.spotify.com/v1/me/player`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        })
            .then(async resp => {
                if (resp.status >= 200 && resp.status < 400) {
                    let data = await resp.json();
                    this.playbackStateCacheTime = Date.now() + 3000;
                    return this.playbackStateCache = data;
                }
            })
            .catch((err) => console.error("Can't get spotify playback state", err))

    }
    async seek(second: number) {
        await fetch(`https://api.spotify.com/v1/me/player/seek?position_ms=${second * 1000}&device_id=${await window.electronAPI.getSpotifyId()}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }).catch((err) => console.error("Can't seek on spotify", err));
    }



}


type SpotifyApiPlaybackState = {
    device: { id: string, volume_percent: number },
    context: { uri: string },
    progress_ms: number,
    item: { name: string, duration_ms: number, uri: string, album: { images: [{ url: string }] } },
    is_playing: boolean
}

