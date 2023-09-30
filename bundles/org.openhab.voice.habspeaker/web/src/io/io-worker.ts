/// <reference lib="webworker" />

import { SINK_TERMINATION_BYTE, StreamType, WebSocketInCmd, WebSocketInCmdType, WebSocketOutCmd, WebSocketOutCmdType, WorkerInCmd, WorkerInCmdType, WorkerOutCmd, WorkerOutCmdType } from "./io-types";
import { MessageACKManager } from "./message-ack-manager";
import { createResampler, Resampler } from "./resampler";
import { CircularBufferExecutor } from "./circular-buffer";
import { ReentrantLock } from "reentrant-lock";

/** Size of the circular buffer used to ensure a constant chunk size */
const SINK_CHUNK_SIZE = 4096;


/** Contains the state and resources of an active sink */
type SinkContext = {
  /** The resampler implementation used, can be a noop */
  resampler: Resampler;
  /** Circular buffer executor used to ensure a constant chunk size */
  bufferedExecutor: CircularBufferExecutor<Float32Array>;
  /** Cache to be used until the message port is available */
  buffersCache: Float32Array[];
  /** Indicates that the data streaming from the server has ended */
  streamEnded: boolean;
  /** Message port used to feed the chunks into the audio system */
  port?: MessagePort;
};
type PostMessage = typeof postMessage;

/**
 * Handles the websocket connection and the required audio format conversions. 
 */
export default class IOWorker {
  /** A WebSocket connection to openHAB */
  socket?: WebSocket;
  /** Speaker id */
  id = "";
  /** OpenHAB token */
  token = "";
  /** Sample rate of the audio context sample rate */
  sampleRate = 0;
  /** Sample rate of the audio send between openHAB and the UI */
  streamSampleRate = 0;
  /** Used to receive the audio data from the WebAudioAPI */
  sourcePort?: MessagePort;
  /** Holds the resampler for the audio source data */
  sourceResampler?: Resampler;
  /** Stores each sink context by its id */
  sinkContextStorage = new Map<string, SinkContext>();
  /** Lock used to ensure sink data chunks are processed in order */
  sinkLock = new ReentrantLock();
  /** Defines the resampler implementation to use */
  resamplerMode: string = "";
  /** Holds the openHAB server url */
  ohUrl: string = '';
  /** Used to wait for some tasks to be done on the main thread */
  ackManager = new MessageACKManager("worker");
  /** Token used to wait until the main thread has handled the speaker configuration message */
  configurationACK?: number;
  constructor(private postMessage: PostMessage) {
  }
  /**
   * Sends a {@link WorkerOutCmd} to the main thread
   */
  postToMainThread<T extends WorkerOutCmd>(cmd: T, args?: WorkerOutCmdType<T>) {
    this.postMessage({ cmd, ...(args ?? {}) });
  }
  /**
   *
   * Send a {@link WebSocketInCmd} to the openHAB server
   */
  postToWebSocket<T extends WebSocketInCmd>(cmd: T, args?: WebSocketInCmdType<T>) {
    if (this.socket && this.socket.readyState == this.socket.OPEN) {
      this.socket.send(JSON.stringify({ cmd, ...args }));
    } else {
      console.error("post cmd " + cmd + ": WebSocket is not connected");
    }
  }
  /**
   * Handles the {@link WorkerInCmd} received from the main thread
   */
  onMainThreadCommand(ev: any) {
    try {
      if (ev.origin !== "" || typeof ev.data !== 'object') {
        return;
      }
      const command = ev.data.cmd as WorkerInCmd;
      switch (command) {
        case WorkerInCmd.INITIALIZE:
          const initData = ev.data as WorkerInCmdType<typeof command>;
          this.id = initData.id;
          this.sampleRate = initData.sampleRate;
          this.streamSampleRate = initData.sampleRate;
          this.sourceResampler = undefined;
          this.token = initData.token ?? '';
          this.ohUrl = initData.ohUrl
            .replace('https:', 'wss:')
            .replace('http:', 'ws:');
          this.connectWebSocket();
          break;
        case WorkerInCmd.ACK_MESSAGE:
          const ackData = ev.data as WorkerInCmdType<typeof command>;
          this.ackManager.confirmACK(ackData.code);
          break;
        case WorkerInCmd.SOURCE_PORT:
          const listenData = ev.data as WorkerInCmdType<typeof command>;
          this.sourcePort?.close();
          this.sourcePort = listenData.port;
          this.sourcePort.onmessage = (ev) => this.handleSourceAudioBuffer(ev.data);
          this.sourcePort.start();
          this.postToMainThread(WorkerOutCmd.ACK_MESSAGE, { code: listenData.ack });
          break;
        case WorkerInCmd.SINK_PORT:
          const speakPortData = ev.data as WorkerInCmdType<typeof command>;
          const sinkContext = this.sinkContextStorage.get(speakPortData.id);
          if (sinkContext) {
            const sinkPort = sinkContext.port = speakPortData.port;
            sinkPort.onmessage = (ev) => {
              if (ev.data === false) {
                // clean up sink context
                console.debug("cleaning up sink ", speakPortData.id);
                this.sinkContextStorage.delete(speakPortData.id);
                sinkContext.resampler.close();
                sinkPort.close();
                this.postToMainThread(WorkerOutCmd.STOP_SINK, { id: speakPortData.id });
              }
            };
            sinkPort.start();
            if (sinkContext.buffersCache.length) {
              sinkPort.postMessage(sinkContext.buffersCache);
            }
            if (sinkContext.streamEnded) {
              // notify streamCompletion
              sinkPort.postMessage(false);
            }
          } else {
            console.error("Unable to handle sink port, missing sink context");
          }
          break;
        case WorkerInCmd.ON_SPOT:
          this.postToWebSocket(WebSocketInCmd.ON_SPOT);
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
          if (this.socket) {
            this.socket.close();
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
  /**
   * Handles the {@link WebSocketOutCmd} received from OpenHAB
   */
  onWebSocketCommand(data: any) {
    try {
      if ((import.meta as any).env.DEV) {
        console.debug("websocket => io-worker: ", data);
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
          (async () => {
            if (configureData.sampleRate !== -1) {
              this.streamSampleRate = configureData.sampleRate;
            } else {
              this.streamSampleRate = this.sampleRate;
            }
            this.resamplerMode = configureData.resampleMode;
            this.sourceResampler = await createResampler(this.resamplerMode, this.sampleRate, this.streamSampleRate, 1);
            this.configurationACK = this.ackManager.createACK();
            this.postToMainThread(WorkerOutCmd.CONFIGURE, { ...configureData, ack: this.configurationACK });
            await this.ackManager.awaitACK(this.configurationACK);
            this.configurationACK = undefined;
          })()
            .then(() => this.postToWebSocket(WebSocketInCmd.CONFIGURED))
            .catch((err) => console.error("worker configuration error:", err));
          break;
        case WebSocketOutCmd.SINK_VOLUME:
          const sinkVolumeData = data as WebSocketOutCmdType<typeof command>;
          this.postToMainThread(WorkerOutCmd.SINK_VOLUME, sinkVolumeData);
          break;
        case WebSocketOutCmd.SOURCE_VOLUME:
          const sourceVolumeData = data as WebSocketOutCmdType<typeof command>;
          this.postToMainThread(WorkerOutCmd.SOURCE_VOLUME, sourceVolumeData);
          break;
        case WebSocketOutCmd.MEDIA_COMMAND:
          const mediaCommandData = data as WebSocketOutCmdType<typeof command>;
          this.postToMainThread(WorkerOutCmd.MEDIA_COMMAND, mediaCommandData);
          break;
        default:
          throw new Error("Unknown command " + data.cmd);
      }
    } catch (error) {
      console.error("Error handling command in io-worker: ", error);
    }
  }
  /**
   *  Starts the websocket connection to the openHAB server, with retry on error/disconnection.
   */
  private connectWebSocket() {
    let retryRef: any = null;
    const retry = () => {
      if (retryRef) {
        clearTimeout(retryRef);
        retryRef = null;
      }
      retryRef = setTimeout(this.connectWebSocket.bind(this), 10000);
    };
    let wsRef = this.socket;
    let query = "";
    if (this.token.length) {
      query = `?accessToken=${this.token}`
    }
    try {
      wsRef = this.socket = new WebSocket(`${this.ohUrl}/ws/habspeaker${query}`);
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
        console.debug("io-worker => websocket:", initMessage);
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
          // incoming command
          this.onWebSocketCommand(JSON.parse(msg.data));
          break;
        case "object":
          if (msg.data instanceof Blob) {
            // incoming audio
            const blob = msg.data;
            this.sinkLock
              .lock(() => blob.arrayBuffer().then((buffer) => this.handleSinkAudioBuffer(buffer)))
              .catch(err => console.error("io-worker: error on sink blob", err));
            if ((import.meta as any).env.DEV) {
              console.debug("websocket => io-worker: Binary data");
            }
          }
          break;
        default:
          console.error(
            "websocket => io-worker: unprocessed message typeof " + msgType
          );
      }
    });
    wsRef.addEventListener("close", () => {
      console.warn("websocket => io-worker: connection closed");
      if (this.configurationACK != null) {
        this.ackManager.abortACK(this.configurationACK);
        this.configurationACK = undefined;
      }
      this.sourcePort?.close();
      this.sourcePort = undefined;
      this.sourceResampler?.close();
      this.sourceResampler = undefined;
      this.socket = undefined;
      this.postToMainThread(WorkerOutCmd.OFFLINE);
      retry();
    });
    wsRef.addEventListener("error", (err) => console.error("ERROR:", err));
    return wsRef;
  }
  /**
   * Sends audio though a {@link WebSocket} after encode it as a int 16 buffer.
   * Resamples the audio when needed from audio context sample rate to the stream sample rate.
   */
  private handleSourceAudioBuffer(buffer: Float32Array) {
    if (this.socket) {
      if (!this.sourceResampler) {
        console.error("Error sending audio to oh: Resampler not initialized");
        return;
      }
      const resampled = this.sourceResampler.resample(buffer);
      this.socket.send(audioToInt16Buffer(resampled));
    } else {
      console.error("Error sending audio to oh: WebSocket is not connected");
    }
  }
  /**
   * Sends audio to the audio system, after encode it as a float 32 buffer.
   * When it gets a new sink id (extracted from the buffer), it creates a sink context, request the required setup to the main thread.
   * 
   * If there is message port in the correspondent {@link SinkContext} sends audio though it,
   * else cache the audio into the sink context cache, so it can be send when the port is ready.
   * 
   * Resamples the audio when needed from stream sample rate to the audio context sample rate.
   */
  private async handleSinkAudioBuffer(buffer: ArrayBuffer) {
    // First 4 bytes from each chunk contains the stream id
    const streamId = new Uint8Array(buffer.slice(0, 4)).join('-');
    // Fifth byte from each chunk contains the stream type
    const streamType = new Uint8Array(buffer.slice(4, 5)).toString();
    let channels: number;
    switch (streamType) {
      case StreamType.PCM16BitMono:
        channels = 1;
        break;
      case StreamType.PCM16BitStereo:
        channels = 2;
        break;
      default:
        console.error("Unknown stream type, aborting: ", streamType);
        return;
    }
    const dataBuffer = buffer.slice(5);
    let sinkContext = this.sinkContextStorage.get(streamId) as SinkContext;
    if (!sinkContext) {
      const sendSinkData = (buffer: Float32Array) => {
        const resampledBuffer = sinkContext.resampler.resample(buffer);
        if (sinkContext.port) {
          sinkContext.port.postMessage(resampledBuffer);
        } else {
          sinkContext.buffersCache.push(resampledBuffer.slice());
        }
      };
      sinkContext = {
        resampler: await createResampler(this.resamplerMode, this.streamSampleRate, this.sampleRate, channels),
        bufferedExecutor: new CircularBufferExecutor(new Float32Array(SINK_CHUNK_SIZE), sendSinkData),
        buffersCache: [],
        streamEnded: false,
        port: undefined,
      };
      this.sinkContextStorage.set(streamId, sinkContext);
      // request the setup of a sink to the main thead
      this.postToMainThread(WorkerOutCmd.START_SINK, {
        id: streamId,
        channels: channels,
      });
    }
    if (dataBuffer.byteLength === 1) {
      if (SINK_TERMINATION_BYTE === new Uint8Array(dataBuffer).toString()) {
        sinkContext.streamEnded = true;
        if (sinkContext.port) {
          sinkContext.port.postMessage(false);
        }
        return;
      }
    }
    // transform the incoming buffer to the browser format and send
    sinkContext.bufferedExecutor
      .process(audioFromInt16Buffer(dataBuffer))
      .catch(err => console.error("Error sending sink data:", err));
  }
}
// WAV conversion utils
/**
 * Converts a Float32Array into a int16 ArrayBuffer, the required by the server.
 */
function audioToInt16Buffer(input: Float32Array): ArrayBuffer {
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
function audioFromInt16Buffer(buffer: ArrayBuffer): Float32Array {
  const view = new DataView(buffer);
  const result = new Float32Array(buffer.byteLength / 2);
  for (let i = 0, offset = 0; i < buffer.byteLength / 2; i += 1, offset += 2) {
    const intValue = view.getInt16(offset, true);
    const floatValue = intValue / 0x8000;
    result[i] = Math.max(-1, Math.min(1, floatValue));
  }
  return result;
}

// bind the WebWorker context to an IOWorker instance
if (typeof postMessage !== "undefined") {
  const ioWorker = new IOWorker(postMessage.bind(this));
  onmessage = ioWorker.onMainThreadCommand.bind(ioWorker);
}
