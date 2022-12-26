
// raw audio
export enum StreamType {
  // 16000Hz 16bit int 1 channel little-endian
  PCM16BitMono = "1",
  // 16000Hz 16bit int 2 channel little-endian
  PCM16BitStereo = "2",
}
// message types
// Some reused message types
export type MediaStateCmd = { totalSeconds: number, currentSecond: number, state: string, volume: number, provider: string, id: string };
type SetVolumeCmd = { value: number };
type ConfigureSpeakerCmd = { sinkVolume?: number, spotMode?: string, screenSaverTime?: number, spotifyToken?: string, label?: string, spotConfig?: { keyword?: string, threshold?: number, averagedThreshold?: number, eagerMode?: boolean } };
type MediaCommandCmd = { type: 'play' } | { type: 'pause' } | { type: 'stop' } | { type: 'next' } | { type: 'previous' } | { type: 'seek', second: number } | { type: 'volume', level: number } | { type: 'start', provider: string, id: string };
type SpotifyTokenCmd = { token: string };
// Commands from worker to server (no command for sending audio as is sent as binary).
export enum WebSocketInCmd {
  INITIALIZE = "INITIALIZE",
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
  LISTEN = "LISTEN",
  ON_SPOT = "ON_SPOT",
  RESET_CONNECTION = "RESET_CONNECTION",
  TOKEN_RENEW = "TOKEN_RENEW",
  SINK_VOLUME = "SINK_VOLUME",
  MEDIA_STATE = "MEDIA_STATE",
};
export type WorkerInCmdType<T extends WorkerInCmd> = T extends WorkerInCmd.INITIALIZE ? { id: string, sampleRate: number, token?: string, } :
  T extends WorkerInCmd.LISTEN ? { buffers: Float32Array[] } :
  T extends WorkerInCmd.TOKEN_RENEW ? { token: string } :
  T extends WorkerInCmd.SINK_VOLUME ? SetVolumeCmd :
  T extends WorkerInCmd.RESET_CONNECTION ? { id: string } :
  T extends WorkerInCmd.MEDIA_STATE ? MediaStateCmd :
  never;
// Commands from worker to main thread.
export enum WorkerOutCmd {
  CONFIGURE = "CONFIGURE",
  INITIALIZED = "INITIALIZED",
  OFFLINE = "OFFLINE",
  SPEAK = "SPEAK",
  START_LISTENING = "START_LISTENING",
  STOP_LISTENING = "STOP_LISTENING",
  SINK_VOLUME = "SINK_VOLUME",
  MEDIA_COMMAND = "MEDIA_COMMAND",
  SPOTIFY_TOKEN = "SPOTIFY_TOKEN"
};
export type WorkerOutCmdType<T extends WorkerOutCmd> = T extends WorkerOutCmd.SPEAK ? { id: string, buffer: Float32Array, channels: number } :
  T extends WorkerOutCmd.CONFIGURE ? ConfigureSpeakerCmd :
  T extends WorkerOutCmd.SINK_VOLUME ? SetVolumeCmd :
  T extends WorkerOutCmd.MEDIA_COMMAND ? MediaCommandCmd :
  T extends WorkerOutCmd.SPOTIFY_TOKEN ? SpotifyTokenCmd :
  never;