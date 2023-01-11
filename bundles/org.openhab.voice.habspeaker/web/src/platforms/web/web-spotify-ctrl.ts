import { MediaProvider, MediaSessionCtrl, PlaybackState } from "../../stores/media-players/media-session";
import { SpotifyPlatformCtrl, SpotifyPlaybackListener } from "../platform";

export class WebSpotifyCtrl implements SpotifyPlatformCtrl {
    token?: string;
    playerId: string = "";
    private _mediaController?: MediaSessionCtrl;
    private player?: Spotify.Player;
    private playbackListener?: SpotifyPlaybackListener;
    async isEnabled(): Promise<boolean> {
        return !!this.token?.length && typeof window.Spotify !== 'undefined';
    }
    async initSpotify(): Promise<void> {
        await this.initPlayer('HAB Speaker')
    }
    async activateSpotify(): Promise<void> {
        await this.player?.activateElement();
    }
    async connect(): Promise<boolean> {
        return this.player?.connect() ?? false;
    }
    async disconnect(): Promise<void> {
        this.player?.disconnect();
    }
    async setPlaybackStateListener(listener: SpotifyPlaybackListener): Promise<void> {
        this.playbackListener = listener;
    }
    async setToken(token: string) {
        this.token = token;
    }
    async initPlayer(name: string) {
        if (this.player != null) {
            this.player.disconnect();
        } else {
            await this.loadSpotifyApi();
        }
        const playerRef = new window.Spotify.Player({
            name,
            getOAuthToken: cb => cb(this.token ?? ''),
        });
        playerRef.addListener('ready', ({ device_id }) => {
            this.playerId = device_id;
            console.debug('Ready with Device ID', device_id);
        });
        playerRef.addListener('autoplay_failed', () => {
            console.warn('Autoplay is not allowed by the browser autoplay rules');
        });
        playerRef.addListener('not_ready', ({ device_id }) => {
            console.debug('Device ID has gone offline', device_id);
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
            this._mediaController = {
                getId: () => MediaProvider.SPOTIFY,
                getMediaId: async () => (await playerRef.getCurrentState())?.context?.metadata?.current_item?.uri ?? "",
                getPlaylistId: async () => (await playerRef.getCurrentState())?.context?.uri ?? undefined,
                getPlaylistIndex: async () => (await playerRef.getCurrentState())?.position ?? 0,
                play: () => playerRef.resume(),
                pause: () => playerRef.pause(),
                stop: () => playerRef.pause(),
                next: () => playerRef.nextTrack(),
                previous: () => playerRef.previousTrack(),
                seek: (second) => playerRef.seek(second * 1000),
                getCurrentSecond: async () => ((await playerRef.getCurrentState())?.position ?? 0) / 1000,
                getTotalSeconds: async () => ((await playerRef.getCurrentState())?.duration ?? 0) / 1000,
                getPlaybackState: async () =>  this.parseSpotifyPlayerState(await playerRef.getCurrentState()),
                getAwakeScreen: () => false,
                getVolume: async () => (await playerRef.getVolume()) * 100,
                setVolume: (value: number) => playerRef.setVolume(value / 100)
            };
        };
        playerRef.addListener('player_state_changed', (playbackState) => {
            this.playbackListener?.(
                this.parseSpotifyPlayerState(playbackState),
                playbackState?.track_window?.current_track?.album?.images?.[0]?.url ?? '',
                playbackState?.track_window?.current_track?.name,
            );
        });
        playerRef.on('ready', readyCallback);
        this.player = playerRef;
    }
    async claimPlayback() {
        if (!this.playerId.length) {
            console.warn("Missing spotify id");
            return;
        }
        await this.withRetry(() => fetch('https://api.spotify.com/v1/me/player', {
            method: 'PUT',
            body: JSON.stringify({ "device_ids": [this.playerId] }),
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }));
    }
    async playOnThisDevice(spotifyUri: string) {
        this.withRetry(() => fetch(`https://api.spotify.com/v1/me/player/play?device_id=${this.playerId}`, {
            method: 'PUT',
            body: JSON.stringify(spotifyUri.startsWith("spotify:track:") ? { uris: [spotifyUri] } : { context_uri: spotifyUri }),
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        })).then(() => console.debug("Spotify starting"))
            .catch((err) => console.error("Can not play on spotify", err));
    }
    async activatePlayer() {
        await this.player?.activateElement();
    }
    private loadSpotifyApi() {
        return new Promise<void>((resolve, reject) => {
            try {
                if (typeof window.Spotify === 'undefined') {
                    var tag = document.createElement('script');
                    tag.src = "https://sdk.scdn.co/spotify-player.js";
                    var firstScriptTag = document.getElementsByTagName('script')[0];
                    if (!firstScriptTag || !firstScriptTag.parentNode)
                        throw new Error('Unable to load spotify api');
                    (window as any).onSpotifyWebPlaybackSDKReady = function () {
                        console.debug("spotify api loaded!");
                        delete (window as any).onSpotifyWebPlaybackSDKReady;
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
    async getPlayer(): Promise<MediaSessionCtrl | undefined> {
        return this?._mediaController;
    }
    private parseSpotifyPlayerState(playerState: Spotify.PlaybackState | null) {
        if (!playerState) {
            return PlaybackState.STOPPED;
        }
        if (playerState.loading) {
            return PlaybackState.BUFFERING;
        }
        if (!playerState.paused) {
            return PlaybackState.PLAYING;
        }
        return PlaybackState.PAUSED;
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