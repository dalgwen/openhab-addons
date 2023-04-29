import { ReentrantLock } from "reentrant-lock";
import { StreamType, WebSocketInCmd, WebSocketInCmdType, WebSocketOutCmd, WebSocketOutCmdType, WorkerInCmd, WorkerInCmdType, WorkerOutCmd, WorkerOutCmdType } from "./io-types";
import { MessageACKManager } from "./message-ack-manager";
import { Resampler } from "./resampler";
const SINK_RESAMPLER_CHUNK_SIZE = 4096;
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
  sinkContextStorage = new Map<string, { resampler: Resampler, resamplerBuffer: Float32Array, port?: MessagePort, buffersCache?: Float32Array[] }>();
  sinkLock = new ReentrantLock();
  ohUrl: string = '';
  listenPort?: MessagePort;
  messageACKs = new MessageACKManager("worker");
  configurationACK?: number;
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
        case WorkerInCmd.ACK_MESSAGE:
          const ackData = ev.data as WorkerInCmdType<typeof command>;
          this.messageACKs.confirmACK(ackData.code);
          break;
        case WorkerInCmd.LISTEN_PORT:
          const listenData = ev.data as WorkerInCmdType<typeof command>;
          this.listenPort = listenData.port;
          this.listenPort.onmessage = (ev) => {
            this.onListen(ev.data);
          };
          this.listenPort.start();
          this.postToMainThread(WorkerOutCmd.ACK_MESSAGE, { code: listenData.ack });
          break;
        case WorkerInCmd.SPEAK_PORT:
          const speakPortData = ev.data as WorkerInCmdType<typeof command>;
          if (speakPortData.port) {
            const sinkContext = this.sinkContextStorage.get(speakPortData.id);
            if (sinkContext) {
              sinkContext.port = speakPortData.port;
              if (sinkContext.buffersCache != null) {
                sinkContext.buffersCache.forEach(b => speakPortData.port.postMessage(b, [b.buffer]));
                sinkContext.buffersCache = undefined;
              }
            }
          } else {
            // clean up related sink data
            this.sinkContextStorage.delete(speakPortData.id);
          }
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
        resampler = new Resampler(this.sampleRate, 16000, 1, channelBuffer.length);
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
          if (this.configurationACK != null) {
            this.messageACKs.awaitACK(this.configurationACK)
              .then(() => this.postToMainThread(WorkerOutCmd.INITIALIZED))
              .catch(() => console.error("worker: speaker initialization aborted"));
            this.configurationACK = undefined;
          } else {
            this.postToMainThread(WorkerOutCmd.INITIALIZED);
          }
          break;
        case WebSocketOutCmd.START_LISTENING:
          this.postToMainThread(WorkerOutCmd.START_LISTENING);
          break;
        case WebSocketOutCmd.STOP_LISTENING:
          this.postToMainThread(WorkerOutCmd.STOP_LISTENING);
          break;
        case WebSocketOutCmd.CONFIGURE:
          const configureData = data as WebSocketOutCmdType<typeof command>;
          this.configurationACK = this.messageACKs.createACK();
          this.postToMainThread(WorkerOutCmd.CONFIGURE, { ...configureData, ack: this.configurationACK });
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
    let retryRef: any = null;
    const retry = () => {
      if (retryRef) {
        clearTimeout(retryRef);
        retryRef = null;
      }
      retryRef = setTimeout(this.connectWebSocket.bind(this), 10000);
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
            this.sinkLock.lock(() => this.sendIncomingAudioBlob(blob)).catch(err => console.error(err));
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
      if (this.configurationACK != null) {
        this.messageACKs.abortACK(this.configurationACK);
        this.configurationACK = undefined;
      }
      this.wsRef = undefined;
      this.postToMainThread(WorkerOutCmd.OFFLINE);
      retry();
    });
    wsRef.addEventListener("error", (err) => console.error("ERROR:", err));
    return wsRef;
  }

  private sendIncomingAudioBlob(blob: Blob) {
    return blob.arrayBuffer().then((buffer) => {
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
      let sinkMetadata = this.sinkContextStorage.get(streamId);
      if (!sinkMetadata) {
        sinkMetadata = {
          resampler: new Resampler(streamSampleRate, this.sampleRate, streamChannels, SINK_RESAMPLER_CHUNK_SIZE * streamChannels),
          resamplerBuffer: new Float32Array(),
          buffersCache: [],
          port: undefined
        };
        this.sinkContextStorage.set(streamId, sinkMetadata);
        // request the creation of the sink message port
        this.postMessage({
          cmd: WorkerOutCmd.SPEAK_PORT,
          id: streamId,
          channels: streamChannels,
        });
      }
      // transform the incoming buffer to the browser format
      const floatBuffer = audioFromInt16Buffer(dataBuffer);
      // push data to the resampler buffer
      const mergedBuffer = new Float32Array(sinkMetadata.resamplerBuffer.length + floatBuffer.length);
      mergedBuffer.set(sinkMetadata.resamplerBuffer, 0);
      mergedBuffer.set(floatBuffer, sinkMetadata.resamplerBuffer.length);
      sinkMetadata.resamplerBuffer = mergedBuffer;
      // process the resampler buffer
      while (sinkMetadata.resamplerBuffer.length >= SINK_RESAMPLER_CHUNK_SIZE) {
        const chunk = sinkMetadata.resamplerBuffer.slice(0, SINK_RESAMPLER_CHUNK_SIZE);
        sinkMetadata.resamplerBuffer = sinkMetadata.resamplerBuffer.slice(SINK_RESAMPLER_CHUNK_SIZE);
        const resampledBuffer = sinkMetadata.resampler.resample(chunk);
        if (sinkMetadata.port) {
          // send data over the sink message port
          sinkMetadata.port.postMessage(resampledBuffer, [resampledBuffer.buffer]);
        } else if (sinkMetadata.buffersCache) {
          // cache this buffer
          sinkMetadata.buffersCache.push(resampledBuffer);
        }
      }
    });
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
    output.setInt16(offset, s * 0x8000, true);
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
    const floatValue = intValue / 0x8000;
    result[i] = Math.max(-1, Math.min(1, floatValue));
  }
  return result;
}

// worker start up
if (typeof postMessage !== "undefined") {
  const ioWorker = new IOWorker(postMessage.bind(this));
  onmessage = ioWorker.onMainThreadCommand.bind(ioWorker);
}
