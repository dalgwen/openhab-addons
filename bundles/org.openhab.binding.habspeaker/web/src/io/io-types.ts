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
  suspendOnHide: boolean,
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
// Commands from worker to server (no command for sending audio as it is sent as binary message).
export enum WebSocketInCmd {
  // Instruct the server to start initialization.
  INITIALIZE = "INITIALIZE",
  // Notifies the server that the configuration message has been processed and the client is ready.
  CONFIGURED = "CONFIGURED",
  // Notifies the server local spot, so it can trigger the dialog processing execution.
  ON_SPOT = "ON_SPOT",
  // Notifies the server about the client media state.
  MEDIA_STATE = "MEDIA_STATE",
}
export type WebSocketInCmdType<T extends WebSocketInCmd> =
  T extends WebSocketInCmd.INITIALIZE ? { id: string, sampleRate: number } :
  T extends WebSocketInCmd.CONFIGURED ? { sinkVolume: number; sourceVolume: number; mediaVolume: number; } :
  T extends WebSocketInCmd.MEDIA_STATE ? MediaStateCmd :
  never;

// Commands from server to worker (no command for receiving audio as is sent as binary).
export enum WebSocketOutCmd {
  // Message with speaker configuration.
  CONFIGURE = "CONFIGURE",
  // Message that confirm initialization has completed and the sink and source and dialog has been setup in OpenHAB.
  INITIALIZED = "INITIALIZED",
  // Message that instruct the client to start sending the microphone audio.
  START_LISTENING = "START_LISTENING",
  // Message that instruct the client to stop sending the microphone audio.
  STOP_LISTENING = "STOP_LISTENING",
  // Message that notifies a new sink volume.
  SINK_VOLUME = "SINK_VOLUME",
  // Message that notifies a new source volume.
  SOURCE_VOLUME = "SOURCE_VOLUME",
  // Message with a media command (play, pause, seek ...).
  MEDIA_COMMAND = "MEDIA_COMMAND",
}


export type WebSocketOutCmdType<T extends WebSocketOutCmd> = T extends WebSocketOutCmd.CONFIGURE ? ConfigureSpeakerCmd :
  T extends WebSocketOutCmd.SINK_VOLUME ? SetVolumeCmd :
  T extends WebSocketOutCmd.MEDIA_COMMAND ? MediaCommandCmd :
  never;
// Commands from main thread to worker.
export enum WorkerInCmd {
  // Notifies the worker thread to start the speaker ws connection with reconnection logic.
  INITIALIZE = "INITIALIZE",
  // Notifies the worker thread that the configuration message has been handled.
  CONFIGURED = "CONFIGURED",
  // Notifies the worker thread to resume the speaker ws connection and reconnection logic.
  RESUME = "RESUME",
  // Notifies the worker thread to suspend the speaker ws connection and reconnection logic.
  SUSPEND = "SUSPEND",
  // Get a message port to receive the microphone audio stream.
  SOURCE_PORT = "SOURCE_PORT",
  // Get a message port to send a sink audio stream.
  SINK_PORT = "SINK_PORT",
  // Tell the worker to send a spot event to the server. Used for the button and local keyword spotter.
  ON_SPOT = "ON_SPOT",
  // Tell the worker to force a disconnection without removing the reconnection logic, so it will reconnect again after some seconds. 
  RESET_CONNECTION = "RESET_CONNECTION",
  // Share api authorization token renews to the worker so it can reopen the ws connection if needed. 
  TOKEN_RENEW = "TOKEN_RENEW",
  // Send the media state to the worker so it can proxy it to the server.
  MEDIA_STATE = "MEDIA_STATE",
}
export type WorkerInCmdType<T extends WorkerInCmd> = T extends WorkerInCmd.INITIALIZE ? { id: string, sampleRate: number, token?: string, ohUrl: string } :
  T extends WorkerInCmd.SOURCE_PORT ? { port: MessagePort } :
  T extends WorkerInCmd.SINK_PORT ? { id: string, port: MessagePort } :
  T extends WorkerInCmd.TOKEN_RENEW ? { token: string } :
  T extends WorkerInCmd.RESET_CONNECTION ? { id: string } :
  T extends WorkerInCmd.MEDIA_STATE ? MediaStateCmd :
  never;
// Commands from worker to main thread.
export enum WorkerOutCmd {
  // Send the speaker config to main thread.
  CONFIGURE = "CONFIGURE",
  // Notifies main thread speaker connection is ready.
  INITIALIZED = "INITIALIZED",
  // Notifies main thread that the server is ready to start receiving audio.
  SOURCE_READY = "SOURCE_READY",
  // Notifies main thread speaker connection was closed.
  OFFLINE = "OFFLINE",
  // Notifies the main thread to setup the sink required resources and transfer its message port to the worker so the audio can be sent without using the main thread.
  START_SINK = "START_SINK",
  // Notifies the main thread to tear down the sink required resources.
  STOP_SINK = "STOP_SINK",
  // Notifies the main thread to setup the source audio processor and transfer its message port to the worker so the audio can be received without using the main thread.
  START_LISTENING = "START_LISTENING",
  // Notifies the main thread to teardown the source audio processor.
  STOP_LISTENING = "STOP_LISTENING",
  // Notifies main thread the selected sink volume.
  SINK_VOLUME = "SINK_VOLUME",
  // Notifies main thread the selected source volume.
  SOURCE_VOLUME = "SOURCE_VOLUME",
  // Notifies main thread about media control commands.
  MEDIA_COMMAND = "MEDIA_COMMAND",
}
export type WorkerOutCmdType<T extends WorkerOutCmd> = T extends WorkerOutCmd.CONFIGURE ? ConfigureSpeakerCmd :
  T extends WorkerOutCmd.START_SINK ? { id: string, channels: number } :
  T extends WorkerOutCmd.STOP_SINK ? { id: string } :
  T extends WorkerOutCmd.SINK_VOLUME ? SetVolumeCmd :
  T extends WorkerOutCmd.SOURCE_VOLUME ? SetVolumeCmd :
  T extends WorkerOutCmd.MEDIA_COMMAND ? MediaCommandCmd :
  never;