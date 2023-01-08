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
    private playbackStateCacheExpiration = 0;
    async initSpotify(): Promise<void> {
        // nothing to do
        window.electronAPI.setSpotifyPlaybackListener(async (state: string) => {
            if (this.playbackListener) {
                if (state == 'play' || state == 'pause') {
                    this.playbackStateCacheExpiration = 0;
                    const playbackState = await this.getSpotifyPlaybackState();
                    if (playbackState) {
                        const imPlating = playbackState.device?.id == (await window.electronAPI.getSpotifyId());
                        if (imPlating) {
                            this.playbackListener(
                                playbackState.is_playing ? PlaybackState.PLAYING : PlaybackState.PAUSED,
                                playbackState.item?.uri ?? '',
                                playbackState.item?.album.images[0].url ?? '',
                                playbackState.item?.name ?? '',
                            );
                        } else {
                            this.playbackListener(
                                PlaybackState.STOPPED,
                                "",
                                "",
                                "",
                            );
                        }
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
            getPlaylistId: async () => (await this.getSpotifyPlaybackState())?.context?.uri,
            getPlaylistIndex: async () => (await this.getSpotifyPlaybackState())?.position,
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
        await this.withRetry(() => fetch('https://api.spotify.com/v1/me/player', {
            method: 'PUT',
            body: JSON.stringify({ "device_ids": [deviceId] }),
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }));
    }
    async spotifyPlay(spotifyUri: string) {
        await this.withRetry(async () => fetch(`https://api.spotify.com/v1/me/player/play?device_id=${await window.electronAPI.getSpotifyId()}`, {
            method: 'PUT',
            body: JSON.stringify(spotifyUri.startsWith("spotify:track:") ? { uris: [spotifyUri] } : { context_uri: spotifyUri }),
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }));
    }
    async spotifyResume() {
        await this.withRetry(async () => fetch(`https://api.spotify.com/v1/me/player/play?device_id=${await window.electronAPI.getSpotifyId()}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }));
    }
    async spotifyPause() {
        await this.withRetry(async () => fetch(`https://api.spotify.com/v1/me/player/pause?device_id=${await window.electronAPI.getSpotifyId()}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }));
    }
    async spotifyNext() {
        await this.withRetry(async () => fetch(`https://api.spotify.com/v1/me/player/next?device_id=${await window.electronAPI.getSpotifyId()}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }));
    }
    async spotifyPrevious() {
        await this.withRetry(async () => fetch(`https://api.spotify.com/v1/me/player/previous?device_id=${await window.electronAPI.getSpotifyId()}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }));
    }

    async setSpotifyVolume(volume: number) {
        await this.withRetry(async () => fetch(`https://api.spotify.com/v1/me/player/volume?device_id=${await window.electronAPI.getSpotifyId()}&volume_percent=${Math.floor(volume)}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }));
    }
    async seekSpotifyPlayback(second: number) {
        await this.withRetry(async () => fetch(`https://api.spotify.com/v1/me/player/seek?device_id=${await window.electronAPI.getSpotifyId()}&position_ms=${Math.floor(second * 1000)}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }));
    }
    async getSpotifyPlaybackState(): Promise<SpotifyApiPlaybackState | undefined> {
        if (this.playbackStateCache && Date.now() < this.playbackStateCacheExpiration) {
            // This avoid doing to much calls to the spotify api, this cache is invalidated on song changes.  
            return { ...this.playbackStateCache, progress_ms: this.playbackStateCache.progress_ms + (Date.now() - this.playbackStateCacheTime) };
        }
        return await this.withRetry(async () => await fetch(`https://api.spotify.com/v1/me/player`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }))
            .then(async resp => {
                if (resp.status >= 200 && resp.status < 400) {
                    this.playbackStateCacheTime = Date.now();
                    this.playbackStateCacheExpiration = Date.now() + 30000;
                    return this.playbackStateCache = await resp.json();
                }
                throw new Error(`Can not get spotify player state with response ${resp.status}: ${resp.statusText}`);
            });
    }
    async seek(second: number) {
        return await this.withRetry(async () => await fetch(`https://api.spotify.com/v1/me/player/seek?position_ms=${second * 1000}&device_id=${await window.electronAPI.getSpotifyId()}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }));
    }
    // retry on 50x errors
    private async withRetry(fetchOp: () => Promise<Response>) {
        let count = 0;
        let resp = await fetchOp();
        while (count < 2) {
            if (resp.status < 500) {
                return resp;
            }
            await new Promise((r) => setTimeout(r, 300));
            resp = await fetchOp();
            count++;
        }
        return resp;
    }

}


type SpotifyApiPlaybackState = {
    device: { id: string, volume_percent: number },
    context: { uri: string },
    progress_ms: number,
    position: number;
    item: { name: string, duration_ms: number, uri: string, album: { images: [{ url: string }] } },
    is_playing: boolean
}

