import { StreamType, WebSocketInCmd, WebSocketInCmdType, WebSocketOutCmd, WebSocketOutCmdType, WorkerInCmd, WorkerInCmdType, WorkerOutCmd, WorkerOutCmdType } from "./io-types";
import { Resampler } from "./resampler";
/**
 * Handles the websocket connection and the required audio format conversions. 
 */
export default class IOWorker {
  /**@type {WebSocket} */
  wsRef?: WebSocket;
  /**@type {string} */
  id = "";
  /**@type {string} */
  token = "";
  /**@type {number} */
  sampleRate = 0;
  inputResampler?: Resampler;
  sinkResamplers = new Map<string, Resampler>();
  ohUrl: string = '';
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
      console.error("post cmd " + cmd + ": WebSocket is not connected");
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
          this.ohUrl = initData.ohUrl
            .replace('https:', 'wss:')
            .replace('http:', 'ws:');
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
            console.error("reset connection: WebSocket is not connected");
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
      let channelBuffer = buffers[0];
      let resampler = this.inputResampler;
      if (!resampler) {
        resampler = new Resampler(this.sampleRate, 16000, 1, channelBuffer.byteLength);
      }
      const resampled = resampler.resample(channelBuffer);
      this.wsRef.send(audioToInt16Buffer(resampled));
    } else {
      console.error("on listen: WebSocket is not connected");
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
    const wsProtocols = ['habspeaker'];
    if (this.token.length) {
      // send the token info as an alternative protocol
      wsProtocols.push(`oh_token-${this.token}`);
    }
    try {
      wsRef = this.wsRef = new WebSocket(`${this.ohUrl}/habspeaker/ws`, wsProtocols);
    } catch (error) {
      console.error(error);
      return retry();
    }
    wsRef.addEventListener("open", () => {
      const initMessage = JSON.stringify({
        cmd: WebSocketInCmd.INITIALIZE,
        id: this.id,
        sampleRate: this.sampleRate,
      });
      if ((import.meta as any).env.DEV) {
        console.debug("worker => websocket:", initMessage);
      }
      if (!wsRef) {
        console.error("on open: Websocket is not connected!")
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
            const blob = msg.data;
            blob.arrayBuffer().then((buffer) => {
              const streamId = new Uint8Array(buffer.slice(0, 4)).join('-');
              const streamType = new Uint8Array(buffer.slice(4, 5)).join('');
              let streamChannels: number;
              let streamSampleRate: number;
              switch (streamType) {
                case StreamType.PCM16BitMono:
                  streamSampleRate = 16000;
                  streamChannels = 1;
                  break;
                case StreamType.PCM16BitStereo:
                  streamSampleRate = 16000;
                  streamChannels = 2;
                  break;
                default:
                  console.error("Unknown stream type, aborting: ", streamType);
                  return;
              }
              const dataBuffer = buffer.slice(5);
              // transform the incoming buffer to the browser format
              let resampler = this.sinkResamplers.get(streamId);
              if (!resampler) {
                resampler = new Resampler(streamSampleRate, this.sampleRate, streamChannels, dataBuffer.byteLength);
                if (this.sinkResamplers.size > 4) {
                  this.sinkResamplers.delete(Array.from(this.sinkResamplers.keys())[this.sinkResamplers.size - 1]);
                }
                this.sinkResamplers.set(streamId, resampler);
              }
              const resampledBuffer = resampler.resample(audioFromInt16Buffer(dataBuffer));
              // resample input
              this.postMessage({
                cmd: WorkerOutCmd.SPEAK,
                id: streamId,
                buffer: resampledBuffer,
                channels: streamChannels,
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
// WAV conversion utils
/**
 * Converts a Float32Array into a int16 ArrayBuffer, the required by the server.
 */
function audioToInt16Buffer(input: Float32Array) {
  const output = new DataView(new ArrayBuffer(input.length * 2));
  let offset = 0;
  for (let i = 0; i < input.length; i += 1, offset += 2) {
    const s = Math.max(-1, Math.min(1, input[i]));
    output.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true);
  }
  return output.buffer;
}

/**
 * Converts a int16 ArrayBuffer into a Float32Array, the required by the browser.
 */
function audioFromInt16Buffer(buffer: ArrayBuffer) {
  const view = new DataView(buffer);
  const result = new Float32Array(buffer.byteLength / 2);
  for (let i = 0, offset = 0; i < buffer.byteLength / 2; i += 1, offset += 2) {
    const intValue = view.getInt16(offset, true);
    const floatValue = intValue < 0 ? intValue / 0x8000 : intValue / 0x7fff;
    result[i] = floatValue;
  }
  return result;
}

// worker start up
if (typeof postMessage !== "undefined") {
  const ioWorker = new IOWorker(postMessage.bind(this));
  onmessage = ioWorker.onMainThreadCommand.bind(ioWorker);
}
