/**
 * Byte that indicates stream termination, (prefixed by "4 bytes id" + "stream type byte" )
 */
export const SINK_TERMINATION_BYTE = "0";
/**
 * Byte that indicates sink stream format, 5 position of each chunk
 */
export enum StreamType {
  // 16bit int 1 channel little-endian
  PCM16BitMono = "1",
  // 16bit int 2 channel little-endian
  PCM16BitStereo = "2",
}
// message types
// Some reused message types
export type MediaStateCmd = { totalSeconds: number, currentSecond: number, state: string, volume: number, provider: string, id: string };
type SetVolumeCmd = { value: number };
export type RustpotterOptions = {
  wakeword: string
  threshold: number
  avgThreshold: number
  scoreRef: number
  bandSize: number
  minScores: number
  eager: boolean
  scoreMode: string
  vadMode: string
  gainNormalizer: boolean
  minGain: number
  maxGain: number
  gainRef?: number
  bandPass: boolean
  lowCutoff: number
  highCutoff: number
};
export type ConfigureSpeakerCmd = {
  sampleRate: number,
  resampleMode: string,
  useAudioElement: boolean,
  sinkVolume?: number,
  sourceVolume?: number,
  spotMode?: string,
  screenSaverTime?: number,
  label?: string,
  dimScreen?: boolean,
  keepAwake?: boolean,
  spotConfig?: RustpotterOptions
  primaryColor?: string,
  secondaryColor?: string,
  tertiaryColor?: string,
  logoUrl?: string
};
export type MediaCommandCmd = { type: 'play' } |
{ type: 'pause' } |
{ type: 'stop' } |
{ type: 'next' } |
{ type: 'previous' } |
{ type: 'seek', second: number } |
{ type: 'volume', value: number } |
{ type: 'claim', provider: string } |
{ type: 'start', provider: string, mediaId: string, second: number };
// Commands from worker to server (no command for sending audio as is sent as binary).
export enum WebSocketInCmd {
  INITIALIZE = "INITIALIZE",
  CONFIGURED = "CONFIGURED",
  ON_SPOT = "ON_SPOT",
  SINK_VOLUME = "SINK_VOLUME",
  SOURCE_VOLUME = "SOURCE_VOLUME",
  MEDIA_STATE = "MEDIA_STATE",
};
export type WebSocketInCmdType<T extends WebSocketInCmd> =
  T extends WebSocketInCmd.SINK_VOLUME ? SetVolumeCmd :
  T extends WebSocketInCmd.SOURCE_VOLUME ? SetVolumeCmd :
  T extends WebSocketInCmd.INITIALIZE ? { id: string, sampleRate: number } :
  T extends WebSocketInCmd.CONFIGURED ? { sinkVolume: number; sourceVolume: number; mediaVolume: number; } :
  T extends WebSocketInCmd.MEDIA_STATE ? MediaStateCmd :
  never;

// Commands from server to worker (no command for receiving audio as is sent as binary).
export enum WebSocketOutCmd {
  CONFIGURE = "CONFIGURE",
  INITIALIZED = "INITIALIZED",
  START_LISTENING = "START_LISTENING",
  STOP_LISTENING = "STOP_LISTENING",
  SINK_VOLUME = "SINK_VOLUME",
  SOURCE_VOLUME = "SOURCE_VOLUME",
  MEDIA_COMMAND = "MEDIA_COMMAND",
}


export type WebSocketOutCmdType<T extends WebSocketOutCmd> = T extends WebSocketOutCmd.CONFIGURE ? ConfigureSpeakerCmd :
  T extends WebSocketOutCmd.SINK_VOLUME ? SetVolumeCmd :
  T extends WebSocketOutCmd.MEDIA_COMMAND ? MediaCommandCmd :
  never;
// Commands from main thread to worker.
export enum WorkerInCmd {
  INITIALIZE = "INITIALIZE",
  SOURCE_PORT = "SOURCE_PORT",
  SINK_PORT = "SINK_PORT",
  ON_SPOT = "ON_SPOT",
  ACK_MESSAGE = "ACK_MESSAGE",
  RESET_CONNECTION = "RESET_CONNECTION",
  TOKEN_RENEW = "TOKEN_RENEW",
  SINK_VOLUME = "SINK_VOLUME",
  MEDIA_STATE = "MEDIA_STATE",
};
export type WorkerInCmdType<T extends WorkerInCmd> = T extends WorkerInCmd.INITIALIZE ? { id: string, sampleRate: number, token?: string, ohUrl: string } :
  T extends WorkerInCmd.SOURCE_PORT ? { port: MessagePort, ack: number } :
  T extends WorkerInCmd.SINK_PORT ? { id: string, port: MessagePort } :
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
  START_SINK = "START_SINK",
  STOP_SINK = "STOP_SINK",
  START_LISTENING = "START_LISTENING",
  STOP_LISTENING = "STOP_LISTENING",
  SINK_VOLUME = "SINK_VOLUME",
  SOURCE_VOLUME = "SOURCE_VOLUME",
  MEDIA_COMMAND = "MEDIA_COMMAND",
};
export type WorkerOutCmdType<T extends WorkerOutCmd> = T extends WorkerOutCmd.CONFIGURE ? ConfigureSpeakerCmd & { ack: number } :
  T extends WorkerOutCmd.START_SINK ? { id: string, channels: number } :
  T extends WorkerOutCmd.STOP_SINK ? { id: string } :
  T extends WorkerOutCmd.SINK_VOLUME ? SetVolumeCmd :
  T extends WorkerOutCmd.SOURCE_VOLUME ? SetVolumeCmd :
  T extends WorkerOutCmd.MEDIA_COMMAND ? MediaCommandCmd :
  T extends WorkerOutCmd.ACK_MESSAGE ? { code: number } :
  never;