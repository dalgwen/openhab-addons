import { MediaStateCmd } from "../../io/io-types";
import { WebAudioPlayerFactory } from "./web-audio-player";
import { WebVideoPlayerFactory } from "./web-video-player";
import { ReentrantLock } from "reentrant-lock";
export class MediaCtrl {
    private mediaPlayer?: PlayerCtrl;
    players: MediaPlayerFactory[];
    private mediaStateUpdateSeconds = 5;
    private mediaVolume = 100;
    private mediaVolumeBackup = -1;
    private muteMediaCounter = 0;
    private mediaStateInterval: any = null;
    private mediaStateLock = new ReentrantLock();
    private listener?: (cmd: MediaStateCmd) => Promise<void>;
    constructor(playerRoot: HTMLElement) {
        this.players = [new WebAudioPlayerFactory(playerRoot), new WebVideoPlayerFactory(playerRoot)];
    }
    getPlayer(): PlayerCtrl | null {
        return this.mediaPlayer ?? null;
    }
    async loadMedia(provider: MediaProvider, args: { mediaId: string, startSecond: number }) {
        this.mediaStateLock.lock(async () => {
            let currentProvider = this.mediaPlayer?.getId();
            if (currentProvider && currentProvider !== provider) {
                this.players.find(p => p.getId() === currentProvider)?.killPlayer();
            }
            this.mediaPlayer = await this.players.find(p => p.getId() === provider)?.getPlayer(args.mediaId, this.setMediaState.bind(this));
            if (args.startSecond) {
                await this.mediaPlayer?.seek(args.startSecond);
            }
        });
    }
    async claimMedia(provider: MediaProvider) {
        await this.mediaStateLock.lock(async () => {
            if (!this.players.find(p => p.getId() === provider)?.claim()) {
                console.debug("Media claimed, but managed by locally")
            }
        });
    }
    async stopMedia() {
        await this.mediaStateLock.lock(async () => {
            if (this.mediaPlayer) {
                const currentProvider = this.mediaPlayer.getId();
                await this.players.find(p => p.getId() === currentProvider)?.killPlayer();
            }
        });

    }
    async setMediaState(state: PlaybackState) {
        const player = this.getPlayer();
        if (player == null || state === PlaybackState.STOPPED) {
            clearInterval(this.mediaStateInterval);
            await this.mediaStateLock.lock(async () => {
                await this.listener?.({
                    totalSeconds: 0,
                    currentSecond: 0,
                    state: PlaybackState.STOPPED,
                    volume: await this.getMediaVolume(),
                    provider: "",
                    id: "",
                });
            });
        } else {
            const sendState = () => {
                return this.mediaStateLock.lock(async () => {
                    await this.listener?.({
                        totalSeconds: Math.floor(await player.getTotalSeconds()),
                        currentSecond: Math.floor(await player.getCurrentSecond()),
                        volume: await this.getMediaVolume(),
                        state: await player.getPlaybackState(),
                        provider: player.getId(),
                        id: await player.getMediaId(),
                    });
                });
            };
            setInterval(sendState, this.mediaStateUpdateSeconds * 1000);
            await sendState();

        }
    }
    async getMediaVolume() {
        return this.mediaStateLock.lock(async () => {
            if (this.mediaVolumeBackup === -1) {
                if (this.mediaPlayer) {
                    this.mediaVolume = Math.floor(await this.mediaPlayer.getVolume());
                }
                return this.mediaVolume;
            } else {
                return this.mediaVolumeBackup;
            }
        });
    }
    async setMediaVolume(value: number) {
        return this.mediaStateLock.lock(async () => {
            if (this.mediaVolumeBackup === -1) {
                this.mediaVolumeBackup = value;
            } else {
                this.mediaVolume = value;
                await this.mediaPlayer?.setVolume(this.mediaVolume);
            }
        });
    }

    async muteMediaVolume(mute: boolean) {
        await this.mediaStateLock.lock(async () => {
            if (mute) {
                this.muteMediaCounter += 1;
                if (this.muteMediaCounter > 0 && this.mediaVolumeBackup === -1) {
                    console.debug('mute media volume');
                    this.mediaVolumeBackup = await this.getMediaVolume();
                    this.mediaVolume = 3;
                    await this.mediaPlayer?.setVolume(this.mediaVolume)
                        .catch(err => console.error(err));
                }
            } else {
                this.muteMediaCounter -= 1;
                if (this.muteMediaCounter < 1 && this.mediaVolumeBackup !== -1) {
                    console.debug('unmute media volume');
                    this.mediaVolume = this.mediaVolumeBackup;
                    this.mediaVolumeBackup = -1;
                    await this.mediaPlayer?.setVolume(this.mediaVolume)
                        .catch(err => console.error(err));
                }
            }
        });
    }
    setListener(cb: (cmd: MediaStateCmd) => Promise<void>) {
        this.listener = cb;
    }
}

export interface MediaPlayerFactory {
    getId(): MediaProvider;
    claim(): Promise<boolean>;
    getPlayer(src: string, setMediaState: (state: PlaybackState) => void): Promise<PlayerCtrl>;
    killPlayer(): Promise<void>;
}

export interface PlayerCtrl {
    getId(): string;
    getMediaId(): Promise<string>;
    getAwakeScreen(): boolean;
    getVolume(): Promise<number>;
    setVolume(value: number): Promise<void>;
    play(): Promise<void>;
    pause(): Promise<void>;
    stop(): Promise<void>;
    previous(): Promise<void>;
    next(): Promise<void>;
    seek(second: number): Promise<void>;
    getCurrentSecond(): Promise<number>;
    getTotalSeconds(): Promise<number>;
    getPlaybackState(): Promise<PlaybackState>;
}
export enum MediaProvider {
    AUDIO_PLAYER = 'audio-player',
    VIDEO_PLAYER = 'video-player',
}
export enum PlaybackState {
    PLAYING = 'playing',
    PAUSED = 'paused',
    STOPPED = 'stopped',
    BUFFERING = 'buffering',
}