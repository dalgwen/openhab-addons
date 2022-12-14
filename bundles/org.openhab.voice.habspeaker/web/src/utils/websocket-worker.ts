export default class WebSocketWorker {
  /**@type {WebSocket} */
  wsRef?: WebSocket;
  /**@type {string} */
  id = "";
  /**@type {string} */
  token = "";
  /**@type {number} */
  sampleRate = 0;
  constructor(private postMessage: (data: any) => void) {
  }
  /**
   *
   * @param {string} command
   * @param {any} args
   */
  postToMainThread<T extends WorkerOutCmd>(cmd: T, args?: WorkerOutCmdType<T>) {
    this.postMessage({ cmd, ...(args ?? {}) });
  }
  /**
   *
   * @param {string} command
   * @param {any} args
   */
  postToWebSocket<T extends WebSocketInCmd>(cmd: T, args?: WebSocketInCmdType<T>) {
    if (this.wsRef && this.wsRef.readyState == this.wsRef.OPEN) {
      this.wsRef.send(JSON.stringify({ cmd, ...args }));
    } else {
      console.error("WebSocket is not connected");
    }
  }
  /**
   *
   * @param {MessageEvent<{cmd: string, data: any}>} ev
   */
  onMainThreadCommand(ev: any) {
    try {
      if (ev.origin !== "" || typeof ev.data !== 'object') {
        return;
      }
      if ((import.meta as any).env.DEV) {
        // console.debug("main => worker: ", ev.data);
      }
      const command = ev.data.cmd as WorkerInCmd;
      switch (command) {
        case WorkerInCmd.INITIALIZE:
          const initData = ev.data as WorkerInCmdType<typeof command>;
          this.id = initData.id;
          this.sampleRate = initData.sampleRate;
          this.token = initData.token ?? '';
          this.connectWebSocket();
          break;
        case WorkerInCmd.LISTEN:
          const listenData = ev.data as WorkerInCmdType<typeof command>;
          this.onListen(listenData.buffers);
          break;
        case WorkerInCmd.ON_SPOT:
          this.postToWebSocket(WebSocketInCmd.ON_SPOT);
          break;
        case WorkerInCmd.SINK_VOLUME:
          const volumeData = ev.data as WorkerInCmdType<typeof command>;
          this.postToWebSocket(WebSocketInCmd.SINK_VOLUME, volumeData);
          break;
        case WorkerInCmd.MEDIA_STATE:
          const mediaData = ev.data as WorkerInCmdType<typeof command>;
          this.postToWebSocket(WebSocketInCmd.MEDIA_STATE, mediaData);
          break;
        case WorkerInCmd.TOKEN_RENEW:
          const { token } = ev.data as WorkerInCmdType<typeof command>;
          this.token = token;
          break;
        case WorkerInCmd.RESET_CONNECTION:
          const { id } = ev.data as WorkerInCmdType<typeof command>;
          this.id = id;
          if (this.wsRef) {
            this.wsRef.close();
          } else {
            console.error("WebSocket is not connected");
          }
          break;
        default:
          throw new Error("Unknown command: " + ev.data.cmd);
      }
    } catch (error) {
      console.error("Error handling command in worker: ", error);
    }
  }
  onListen(buffers: Float32Array[]) {
    if (this.wsRef) {
      // convert to websocket format and send as binary
      this.wsRef.send(encodeWAV16BitMonoPCM(buffers, this.sampleRate, 16000));
    } else {
      console.error("WebSocket is not connected");
    }
  }

  onWebSocketCommand(data: any) {
    try {
      if ((import.meta as any).env.DEV) {
        console.debug("websocket => worker: ", data);
      }
      const command = data.cmd as WebSocketOutCmd;
      switch (command) {
        case WebSocketOutCmd.INITIALIZED:
          this.postToMainThread(WorkerOutCmd.INITIALIZED);
          break;
        case WebSocketOutCmd.START_LISTENING:
          this.postToMainThread(WorkerOutCmd.START_LISTENING);
          break;
        case WebSocketOutCmd.STOP_LISTENING:
          this.postToMainThread(WorkerOutCmd.STOP_LISTENING);
          break;
        case WebSocketOutCmd.CONFIGURE:
          const configureData = data as WebSocketOutCmdType<typeof command>;
          this.postToMainThread(WorkerOutCmd.CONFIGURE, configureData);
          break;
        case WebSocketOutCmd.SINK_VOLUME:
          const sinkVolumeData = data as WebSocketOutCmdType<typeof command>;
          this.postToMainThread(WorkerOutCmd.SINK_VOLUME, sinkVolumeData);
          break;
        case WebSocketOutCmd.MEDIA_COMMAND:
          const mediaCommandData = data as WebSocketOutCmdType<typeof command>;
          this.postToMainThread(WorkerOutCmd.MEDIA_COMMAND, mediaCommandData);
          break;
        case WebSocketOutCmd.SPOTIFY_TOKEN:
          const spotifyTokenData = data as WebSocketOutCmdType<typeof command>;
          this.postToMainThread(WorkerOutCmd.SPOTIFY_TOKEN, spotifyTokenData);
          break;
        default:
          throw new Error("Unknown command: " + data.cmd);
      }
    } catch (error) {
      console.error("Error handling command in worker: ", error);
    }
  }
  /**
   *
   * @returns {WebSocket}
   */
  connectWebSocket() {
    const retry = () => {
      setTimeout(this.connectWebSocket.bind(this), 10000);
    };
    let wsRef = this.wsRef;
    try {
      wsRef = this.wsRef = new WebSocket(
        `${location.protocol === "http:" ? "ws" : "wss"}://${location.host
        }/habspeaker/ws`
      );
    } catch (error) {
      console.error(error);
      return retry();
    }
    wsRef.addEventListener("open", () => {
      var initMessage = JSON.stringify({
        cmd: WebSocketInCmd.INITIALIZE,
        id: this.id,
        token: (this.token && this.token.length) ? this.token : null,
        sampleRate: this.sampleRate,
      });
      if ((import.meta as any).env.DEV) {
        console.debug("worker => websocket:", initMessage);
      }
      if (!wsRef) {
        console.error("Websocket is not connected!")
        return;
      }
      wsRef.send(initMessage);
    });
    wsRef.addEventListener("message", (msg) => {
      const msgType = typeof msg.data;
      switch (msgType) {
        case "string":
          this.onWebSocketCommand(JSON.parse(msg.data));
          break;
        case "object":
          if (msg.data instanceof Blob) {
            var blob = msg.data;
            blob.arrayBuffer().then((buffer) => {
              var streamId = new Uint8Array(buffer.slice(0, 4)).join('-');
              var dataBuffer = buffer.slice(4);
              // transform the incoming buffer to native format, should be already in the correct sample rate.
              var floatBuffer = decodeWAV16BitPCM(dataBuffer);
              this.postMessage({
                cmd: WorkerOutCmd.SPEAK,
                id: streamId,
                buffer: floatBuffer,
              });
            });
            if ((import.meta as any).env.DEV) {
              console.debug("websocket => worker: Binary data");
            }
          }
          break;
        default:
          console.error(
            "websocket => worker: unprocessed message typeof " + msgType
          );
      }
    });
    wsRef.addEventListener("close", () => {
      console.warn("websocket => worker: connection closed");
      this.wsRef = undefined;
      this.postToMainThread(WorkerOutCmd.OFFLINE);
      retry();
    });
    wsRef.addEventListener("error", (err) => console.error("ERROR:", err));
    return wsRef;
  }
}
// Some reused message types
type MediaStateCmd = { totalSeconds: number, currentSecond: number, state: string, volume: number, provider: string, id: string };
type SetVolumeCmd = { value: number };
type ConfigureSpeakerCmd = { sinkVolume?: number, sinkStereo?: boolean, remoteSpot?: number, screenSaverTime?: number, spotifyToken?: string, label?: string };
type MediaCommandCmd = { type: 'play' } | { type: 'pause' } | { type: 'stop' } | { type: 'next' } | { type: 'previous' } | { type: 'seek', second: number } | { type: 'volume', level: number } | { type: 'start', provider: string, id: string };
type SpotifyTokenCmd = { token: string };
// Commands from worker to server (no command for sending audio as is sent as binary).
enum WebSocketInCmd {
  INITIALIZE = "INITIALIZE",
  ON_SPOT = "ON_SPOT",
  SINK_VOLUME = "SINK_VOLUME",
  MEDIA_STATE = "MEDIA_STATE",
};
type WebSocketInCmdType<T extends WebSocketInCmd> = T extends WebSocketInCmd.SINK_VOLUME ? { value: number } :
  T extends WebSocketInCmd.MEDIA_STATE ? MediaStateCmd :
  never;

// Commands from server to worker (no command for receiving audio as is sent as binary).
enum WebSocketOutCmd {
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
export type WorkerOutCmdType<T extends WorkerOutCmd> = T extends WorkerOutCmd.SPEAK ? { id: string, buffer: Float32Array } :
  T extends WorkerOutCmd.CONFIGURE ? ConfigureSpeakerCmd :
  T extends WorkerOutCmd.SINK_VOLUME ? SetVolumeCmd :
  T extends WorkerOutCmd.MEDIA_COMMAND ? MediaCommandCmd :
  T extends WorkerOutCmd.SPOTIFY_TOKEN ? SpotifyTokenCmd :
  never;
// WAV conversion utils
/**
 * Convert float to 16bit PCM.
 * @param {Float32Array} input The input buffer.
 */
function floatTo16BitPCM(input: Float32Array) {
  const output = new DataView(new ArrayBuffer(input.length * 2));
  let offset = 0;
  for (let i = 0; i < input.length; i += 1, offset += 2) {
    const s = Math.max(-1, Math.min(1, input[i]));
    output.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true);
  }
  return output.buffer;
}
/**
 *
 * @param {byte[]} buffer
 * @param {int} sampleRate
 * @param {int} targetSampleRate
 * @returns {Float32Array}
 */
function downSampleBuffer(buffer: Float32Array, sampleRate: number, targetSampleRate: number) {
  if (targetSampleRate == sampleRate) {
    return buffer;
  }
  if (targetSampleRate > sampleRate) {
    throw "downsampling rate show be smaller than original sample rate";
  }
  var sampleRateRatio = sampleRate / targetSampleRate;
  var newLength = Math.round(buffer.length / sampleRateRatio);
  var result = new Float32Array(newLength);
  var offsetResult = 0;
  var offsetBuffer = 0;
  while (offsetResult < result.length) {
    var nextOffsetBuffer = Math.round((offsetResult + 1) * sampleRateRatio);
    var accum = 0,
      count = 0;
    for (var i = offsetBuffer; i < nextOffsetBuffer && i < buffer.length; i++) {
      accum += buffer[i];
      count++;
    }
    result[offsetResult] = accum / count;
    offsetResult++;
    offsetBuffer = nextOffsetBuffer;
  }
  return result;
}
/**
 *
 * @param {Float32Array]} buffer
 * @returns Float32Array
 */
function decodeWAV16BitPCM(buffer: ArrayBuffer) {
  const view = new DataView(buffer);
  var result = new Float32Array(buffer.byteLength / 2);
  for (let i = 0, offset = 0; i < buffer.byteLength / 2; i += 1, offset += 2) {
    var intValue = view.getInt16(offset, true);
    var floatValue = intValue < 0 ? intValue / 0x8000 : intValue / 0x7fff;
    result[i] = floatValue;
  }
  return result;
}

/**
 *
 * @param {Float32Array[]} audioBuffers
 * @param {number} channels
 * @param {number} sampleRate
 * @param {number} targetSampleRate
 * @returns ArrayBuffer
 */
function encodeWAV16BitMonoPCM(audioBuffers: Float32Array[], sampleRate: number, targetSampleRate: number) {
  /** @type {Float32Array[]}  */
  let channelBuffer = audioBuffers[0];
  const resampled = downSampleBuffer(
    channelBuffer,
    sampleRate,
    targetSampleRate
  );
  return floatTo16BitPCM(resampled);
}
// worker start up
if (typeof postMessage !== "undefined") {
  const webSocketWorker = new WebSocketWorker(postMessage.bind(this));
  onmessage = webSocketWorker.onMainThreadCommand.bind(webSocketWorker);
}
