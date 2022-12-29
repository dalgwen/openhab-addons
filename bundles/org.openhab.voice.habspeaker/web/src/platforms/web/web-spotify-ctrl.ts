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
                    return this.parseSpotifyPlayerState(await playerRef.getCurrentState());
                },
                getAwakeScreen: () => false,
                getVolume: async () => (await playerRef.getVolume()) * 100,
                setVolume: (value: number) => playerRef.setVolume(value / 100)
            };
        };
        playerRef.addListener('player_state_changed', (playbackState) => {
            this.playbackListener?.(
                this.parseSpotifyPlayerState(playbackState),
                playbackState.track_window.current_track.uri,
                playbackState.track_window.current_track.album.images[0]?.url ?? '',
                playbackState.track_window.current_track.name,
            );
        });
        playerRef.on('ready', readyCallback);
        this.player = playerRef;
    }
    async playOnThisDevice(spotifyUri: string) {
        fetch(`https://api.spotify.com/v1/me/player/play?device_id=${this.playerId}`, {
            method: 'PUT',
            body: JSON.stringify(spotifyUri.startsWith("spotify:track:") ? { uris: [spotifyUri] } : { context_uri: spotifyUri }),
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
        }).then(() => console.debug("Spotify starting"))
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
}