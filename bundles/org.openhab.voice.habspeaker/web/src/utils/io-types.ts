
// raw audio
export enum StreamType {
  // 16000Hz 16bit int 1 channel little-endian
  PCM16BitMono = "1",
  // 16000Hz 16bit int 2 channel little-endian
  PCM16BitStereo = "2",
}
// message types
// Some reused message types
export type MediaStateCmd = { totalSeconds: number, currentSecond: number, state: string, volume: number, provider: string, id: string, playlistId?: string, playlistIndex?: number };
type SetVolumeCmd = { value: number };
export type RustpotterOptions = {
  keyword?: string,
  threshold?: number,
  averagedThreshold: number
  comparatorRef: number
  comparatorBandSize: number
  minScores: number
  scoreMode: string
  gainNormalizerEnabled: boolean
  minGain: number
  maxGain: number
  gainRef?: number
  bandPassEnabled: boolean
  bandPassLowCutoff: number
  bandPassHighCutoff: number
};
type ConfigureSpeakerCmd = { sinkVolume?: number, spotMode?: string, sampleRate: number, resampleMode: string, screenSaverTime?: number, spotifyToken?: string, label?: string, dimScreen?: boolean, keepAwake?: boolean, spotConfig?: RustpotterOptions };
type SpotifyTokenCmd = { token: string };
type MediaCommandCmd = { type: 'play' } |
{ type: 'pause' } |
{ type: 'stop' } |
{ type: 'next' } |
{ type: 'previous' } |
{ type: 'seek', second: number } |
{ type: 'volume', level: number } |
{ type: 'claim', provider: string } |
{ type: 'start', provider: string, mediaId?: string, playlistId?: string, playlistIndex?: number, second?: number };
// Commands from worker to server (no command for sending audio as is sent as binary).
export enum WebSocketInCmd {
  INITIALIZE = "INITIALIZE",
  CONFIGURED = "CONFIGURED",
  ON_SPOT = "ON_SPOT",
  SINK_VOLUME = "SINK_VOLUME",
  MEDIA_STATE = "MEDIA_STATE",
};
export type WebSocketInCmdType<T extends WebSocketInCmd> = T extends WebSocketInCmd.SINK_VOLUME ? { value: number } :
  T extends WebSocketInCmd.MEDIA_STATE ? MediaStateCmd :
  never;

// Commands from server to worker (no command for receiving audio as is sent as binary).
export enum WebSocketOutCmd {
  CONFIGURE = "CONFIGURE",
  INITIALIZED = "INITIALIZED",
  START_LISTENING = "START_LISTENING",
  STOP_LISTENING = "STOP_LISTENING",
  SINK_VOLUME = "SINK_VOLUME",
  MEDIA_COMMAND = "MEDIA_COMMAND",
  SPOTIFY_TOKEN = "SPOTIFY_TOKEN"
}


export type WebSocketOutCmdType<T extends WebSocketOutCmd> = T extends WebSocketOutCmd.CONFIGURE ? ConfigureSpeakerCmd :
  T extends WebSocketOutCmd.SINK_VOLUME ? SetVolumeCmd :
  T extends WebSocketOutCmd.MEDIA_COMMAND ? MediaCommandCmd :
  T extends WebSocketOutCmd.SPOTIFY_TOKEN ? SpotifyTokenCmd :
  never;
// Commands from main thread to worker.
export enum WorkerInCmd {
  INITIALIZE = "INITIALIZE",
  LISTEN_PORT = "LISTEN_PORT",
  SPEAK_PORT = "SPEAK_PORT",
  ON_SPOT = "ON_SPOT",
  ACK_MESSAGE = "ACK_MESSAGE",
  RESET_CONNECTION = "RESET_CONNECTION",
  TOKEN_RENEW = "TOKEN_RENEW",
  SINK_VOLUME = "SINK_VOLUME",
  MEDIA_STATE = "MEDIA_STATE",
};
export type WorkerInCmdType<T extends WorkerInCmd> = T extends WorkerInCmd.INITIALIZE ? { id: string, sampleRate: number, token?: string, ohUrl: string } :
  T extends WorkerInCmd.LISTEN_PORT ? { port: MessagePort, ack: number } :
  T extends WorkerInCmd.SPEAK_PORT ? { id: string, ready: boolean } :
  T extends WorkerInCmd.ACK_MESSAGE ? { code: number } :
  T extends WorkerInCmd.TOKEN_RENEW ? { token: string } :
  T extends WorkerInCmd.SINK_VOLUME ? SetVolumeCmd :
  T extends WorkerInCmd.RESET_CONNECTION ? { id: string } :
  T extends WorkerInCmd.MEDIA_STATE ? MediaStateCmd :
  never;
// Commands from worker to main thread.
export enum WorkerOutCmd {
  CONFIGURE = "CONFIGURE",
  INITIALIZED = "INITIALIZED",
  ACK_MESSAGE = "ACK_MESSAGE",
  OFFLINE = "OFFLINE",
  SPEAK_PORT = "SPEAK_PORT",
  START_LISTENING = "START_LISTENING",
  STOP_LISTENING = "STOP_LISTENING",
  SINK_VOLUME = "SINK_VOLUME",
  MEDIA_COMMAND = "MEDIA_COMMAND",
  SPOTIFY_TOKEN = "SPOTIFY_TOKEN"
};
export type WorkerOutCmdType<T extends WorkerOutCmd> = T extends WorkerOutCmd.SPEAK_PORT ? { id: string, channels: number, port: MessagePort } :
  T extends WorkerOutCmd.CONFIGURE ? ConfigureSpeakerCmd & { ack: number } :
  T extends WorkerOutCmd.ACK_MESSAGE ? { code: number } :
  T extends WorkerOutCmd.SINK_VOLUME ? SetVolumeCmd :
  T extends WorkerOutCmd.MEDIA_COMMAND ? MediaCommandCmd :
  T extends WorkerOutCmd.SPOTIFY_TOKEN ? SpotifyTokenCmd :
  never;