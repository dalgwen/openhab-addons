import { AudioSink } from "./audio-sink";
import { AudioSource } from "./audio-source";
import { WorkerInCmd, RustpotterOptions, MediaStateCmd, WorkerOutCmd, WorkerOutCmdType, ConfigureSpeakerCmd, MediaCommandCmd } from "./io-types";
import { MessageACKManager } from "./message-ack-manager";
import audioPortWorklet from "./audio-source-worklet.ts?sharedworker&url";

export interface IOCallbacks {
  onConnected?: () => void;
  onDisconnected?: () => void;
  onStartListening?: () => void;
  onStopListening?: () => void;
  onStartSpeaking?: () => void;
  onStopSpeaking?: () => void;
  onConfigured?: (config: ConfigureSpeakerCmd) => void;
  onMediaCommand?: (mediaCmd: MediaCommandCmd) => void;
}

export class IOMain {
  private messageACKs = new MessageACKManager("main");

  private audioContext: AudioContext | null = null;
  private audioSource: AudioSource | null = null;
  private worker: Worker | null = null;
  private micStreaming = false;

  private localKsProcessorNode: AudioNode | null = null;
  private stopLocalKsProcessorNode: (() => void) | null = null;

  private activeSinks = new Map<string, AudioSink>();
  private listening: boolean = false;
  private online: boolean = false;
  private sinkVolume: number = 100;
  private remoteSpotMode: boolean = false;
  private listenPortACK?: number;
  private accessToken: string | null = null;

  constructor(private ohUrl: string, private callbacks: IOCallbacks = {}) { }

  private startVoiceAudioContext() {
    if (!this.audioContext) {
      let options: AudioContextOptions = {};
      this.audioContext = new AudioContext(options);
      console.debug(`main: Created audio context with sample rate ${this.audioContext.sampleRate}`);
    }
  }
  private getVoiceAudioContext(): AudioContext {
    if (!this.audioContext) {
      throw new Error('AudioContext not initialized');
    }
    return this.audioContext;
  }

  /**
   * Returns a processor node that sends data through the websocket 
  */
  private async getWSProcessorNode() {
    const audioContext = this.getVoiceAudioContext();
    const _webSocketWorkletNode = new AudioWorkletNode(audioContext, 'habspeaker-source-worklet', { numberOfInputs: 1, numberOfOutputs: 0, channelCount: 1, channelCountMode: 'explicit' });
    this.listenPortACK = this.messageACKs.createACK();
    const command = { cmd: WorkerInCmd.SOURCE_PORT, port: _webSocketWorkletNode.port, ack: this.listenPortACK };
    this.worker?.postMessage(command, [command.port]);
    await this.messageACKs.awaitACK(this.listenPortACK);
    return _webSocketWorkletNode as AudioNode;
  }
  /**
   * Returns a processor node that spots for the keyword
   */
  private async initLocalRustpotterProcessor(keyword: string, options: RustpotterOptions) {
    console.debug("main: starting local keyword spotting using rustpotter");
    const { RustpotterService, ScoreMode } = await import("rustpotter-worklet");
    const wasmModuleUrl = new URL('../../node_modules/rustpotter-worklet/dist/rustpotter_wasm_bg.wasm', import.meta.url);
    const workletModuleUrl = new URL('../../node_modules/rustpotter-worklet/dist/rustpotter-worklet.js', import.meta.url);
    const scoreMode = (ScoreMode[options.scoreMode as any] as any) ?? ScoreMode.max;
    const rp = new RustpotterService({
      workletPath: workletModuleUrl.href,
      wasmPath: wasmModuleUrl.href,
      averagedThreshold: options.averagedThreshold,
      threshold: options.threshold,
      minScores: options.minScores,
      scoreMode: scoreMode,
      minGain: options.minGain,
      maxGain: options.maxGain,
      bandPassEnabled: options.bandPassEnabled,
      bandPassLowCutoff: options.bandPassLowCutoff,
      bandPassHighCutoff: options.bandPassHighCutoff,
      comparatorBandSize: options.comparatorBandSize,
      comparatorRef: options.comparatorRef,
      gainNormalizerEnabled: options.gainNormalizerEnabled,
      gainRef: options.gainRef,
    });
    rp.onspot = (detection) => {
      console.debug('main: keyword spotted', detection);
      this.sendSpot();
    };
    if (this.stopLocalKsProcessorNode) {
      this.stopLocalKsProcessorNode();
    }
    this.stopLocalKsProcessorNode = async () => {
      console.debug("main: stopping local keyword spotting");
      try {
        await rp.close();
      } catch (error) {
        console.warn(error)
      }
    };
    const node = await rp.getProcessorNode(this.getVoiceAudioContext());
    let headers: HeadersInit = {};
    if (this.accessToken?.length) {
      headers["Authorization"] = `Bearer ${this.accessToken}`;
    }
    await rp.addWakewordByPath(`${await this.ohUrl}/rest/habspeaker/rustpotter/${keyword.replaceAll(" ", "_")}`, headers);
    return node;
  }
  private postToWorker(cmd: string, args: { [key: string]: any } = {}) {
    try {
      if (this.worker) {
        this.worker.postMessage({ cmd, ...args });
      } else {
        console.warn("main: Worker not running");
      }
    } catch (error) {
      console.error("main: Unable to post to worker", error);
    }
  }
  public sendSpot() {
    if (this.online && this.audioSource && !this.audioSource.isSuspended()) {
      this.postToWorker(WorkerInCmd.ON_SPOT);
    }
  }
  public resetConnection(id: string) {
    this.postToWorker(WorkerInCmd.RESET_CONNECTION, { id });
  }
  public sendMediaState(state: MediaStateCmd) {
    if (this.online) {
      this.postToWorker(WorkerInCmd.MEDIA_STATE, state);
    }
  }
  public setAuthToken(token: string) {
    this.accessToken = token;
    if (this.worker) {
      this.postToWorker(WorkerInCmd.TOKEN_RENEW, { token });
    }
  }
  private async startMicStreaming() {
    if (!this.micStreaming) {
      this.micStreaming = true;
      console.debug("starting microphone audio streaming");
      const processors: AudioNode[] = [await this.getWSProcessorNode()];
      if (!this.micStreaming) {
        console.warn("main: start microphone audio aborted");
        return;
      }
      if (this.localKsProcessorNode) {
        processors.unshift(this.localKsProcessorNode)
      }
      this.audioSource?.start(...processors).catch((err) => console.error(err));
    } else {
      console.warn("main: trying to start microphone streaming but it's already started!");
    }
  }
  private stopMicStreaming() {
    if (this.micStreaming) {
      console.debug("stopping microphone audio streaming");
      this.micStreaming = false;
      const processors: AudioNode[] = this.localKsProcessorNode ? [this.localKsProcessorNode] : [];
      if (processors.length > 0) {
        this.audioSource?.start(...processors).catch((err) => console.error(err));
      } else {
        this.audioSource?.stop();
      }
    } else {
      console.warn("main: trying to stop microphone streaming but it's already stopped!");
    }
  };
  private async stopAllMicProcessors() {
    this.audioSource?.stop();
    if (this.localKsProcessorNode) {
      this.localKsProcessorNode = null;
      if (this.stopLocalKsProcessorNode) {
        this.stopLocalKsProcessorNode();
        this.stopLocalKsProcessorNode = null;
      }
    }
  };

  // worker setup
  private handleWorkerMessage<T extends WorkerOutCmd>(command: WorkerOutCmd, data: WorkerOutCmdType<any>) {
    switch (command) {
      case WorkerOutCmd.CONFIGURE:
        // TODO: disallow configure after initialized
        const speakerConfig = data as WorkerOutCmdType<typeof command>;
        (async () => {
          if (speakerConfig.sinkVolume != null) {
            this.sinkVolume = speakerConfig.sinkVolume;
          }
          this.remoteSpotMode = false;
          switch (speakerConfig.spotMode) {
            case "server":
              this.remoteSpotMode = true;
              break;
            case "rustpotter_web":
              if (speakerConfig.spotConfig?.keyword) {
                try {
                  const ksAudioProcessor = await this.initLocalRustpotterProcessor(speakerConfig.spotConfig.keyword, { ...speakerConfig.spotConfig });
                  this.localKsProcessorNode = ksAudioProcessor;
                  await this.audioSource?.start(this.localKsProcessorNode);
                } catch (error) {
                  console.error("Unable to start local ks processor", error);
                }
              } else {
                console.warn("main: Missed spotConfig configuration");
              }
              break;
            case "none":
            default:
              break;
          }
          this.callbacks.onConfigured?.(speakerConfig);
          this.postToWorker(WorkerInCmd.ACK_MESSAGE, { code: speakerConfig.ack });
        })();
        break;
      case WorkerOutCmd.ACK_MESSAGE:
        const ackData = data as WorkerOutCmdType<typeof command>;
        this.messageACKs.confirmACK(ackData.code);
        break;
      case WorkerOutCmd.INITIALIZED:
        this.online = true;
        this.callbacks.onConnected?.();
        if (this.remoteSpotMode) {
          console.debug("remote spot enabled, starting mic streaming");
          this.startMicStreaming();
        }
        break;
      case WorkerOutCmd.OFFLINE:
        this.sinkVolume = 0;
        this.remoteSpotMode = false;
        this.stopAllMicProcessors();
        if (this.listening) {
          this.listening = false;
          this.callbacks.onStopListening?.();
        }
        if (this.online) {
          this.online = false;
          this.callbacks.onDisconnected?.();
        }
        if (this.listenPortACK) {
          this.messageACKs.abortACK(this.listenPortACK);
          this.listenPortACK = undefined;
        }
        break;
      case WorkerOutCmd.START_SINK:
        const startSinkCmd = data as WorkerOutCmdType<typeof command>;
        const sink = new AudioSink(startSinkCmd.id, this.getVoiceAudioContext(), startSinkCmd.channels, this.sinkVolume);
        const sinkPortCmd = { cmd: WorkerInCmd.SINK_PORT, id: sink.getId(), port: sink.getMessagePort() };
        this.worker?.postMessage(sinkPortCmd, [sinkPortCmd.port]);
        this.activeSinks.set(sink.getId(), sink);
        const startSpeaking = this.activeSinks.size === 1;
        sink.start().then(() => {
          if (startSpeaking) {
            this.callbacks.onStartSpeaking?.();
          }
        }).catch(err => console.error(err));
        break;
      case WorkerOutCmd.STOP_SINK:
        const stopSinkCmd = data as WorkerOutCmdType<typeof command>;
        const activeSink = this.activeSinks.get(stopSinkCmd.id);
        if (activeSink) {
          console.debug(`main: stopping sink ${stopSinkCmd.id}`);
          this.activeSinks.delete(stopSinkCmd.id);
          activeSink.close();
          if (this.activeSinks.size === 0) {
            this.callbacks.onStopSpeaking?.();
          }
        } else {
          console.error("main: unable to stop sink, not found ", stopSinkCmd.id);
        }
        break;
      case WorkerOutCmd.START_LISTENING:
        if (!this.online) {
          console.debug("main: ignoring start listening message before init");
          return;
        }
        if (!this.listening) {
          this.listening = true;
          this.callbacks.onStartListening?.();
        }
        if (!this.remoteSpotMode) {
          this.startMicStreaming();
        }
        break;
      case WorkerOutCmd.STOP_LISTENING:
        if (!this.online) {
          console.debug("main: ignoring stop listening message before init");
          return;
        }
        if (this.listening) {
          this.listening = false;
          this.callbacks.onStopListening?.();
        }
        if (!this.remoteSpotMode) {
          this.stopMicStreaming();
        }
        break;
      case WorkerOutCmd.SINK_VOLUME:
        const { value } = data as WorkerOutCmdType<typeof command>;
        this.sinkVolume = value;
        this.activeSinks.forEach(sink => sink.setVolume(this.sinkVolume));
        break;
      case WorkerOutCmd.MEDIA_COMMAND:
        const mediaCommandData = data as WorkerOutCmdType<typeof command>;
        const sendCommand = () => this.callbacks.onMediaCommand?.(mediaCommandData);
        if (mediaCommandData.type === 'play' || mediaCommandData.type == 'pause') {
          sendCommand();
        } else {
          // try delay command execution to avoid stressing the cpu, because it causes glitches on mobile devices
          this.runWhenSilence(sendCommand);
        }
        break;
      default:
        console.error(`main: Unknown worker command ${command}`);
    }
  }
  public async initialize(speakerId: string, token: string | null) {
    this.startVoiceAudioContext();
    const audioContext = this.getVoiceAudioContext();
    await audioContext.resume();
    AudioSink.setupAudioElement(audioContext);
    this.audioSource = new AudioSource(audioContext);
    await this.audioSource.resume();
    await AudioSink.registerProcessor(audioContext);
    await audioContext.audioWorklet.addModule(audioPortWorklet);
    // microphone stream checker, to keep the stream alive on undetected disconnections  
    setInterval(() => {
      if (this.audioSource?.isSuspended()) {
        this.audioSource.resume();
      }
    }, 10000);
    document.addEventListener("visibilitychange", () => {
      if (!document.hidden && this.audioSource?.isSuspended()) {
        this.audioSource.resume();
      }
    });
    return new Promise((resolve, reject) => {
      try {
        this.worker = new Worker(new URL("../utils/io-worker.ts", import.meta.url), {
          name: "hab_speaker-worker",
          type: "module",
        });
        this.worker.onmessage = (ev: MessageEvent<any>) => {
          if ((import.meta as any).env.DEV) {
            console.debug("worker => main:", ev.data);
          }
          this.handleWorkerMessage(ev.data.cmd as WorkerOutCmd, ev.data);
        };
        this.worker.onerror = (err) => {
          console.error(err);
          reject(err);
        };
        this.worker?.postMessage({
          cmd: WorkerInCmd.INITIALIZE,
          id: speakerId,
          token,
          sampleRate: this.getVoiceAudioContext().sampleRate,
          ohUrl: this.ohUrl,
        });
        resolve(this.worker);
      } catch (error) {
        reject(error);
      }
    });
  }

  private async runWhenSilence(cb: () => void, delay = 1500, max = 5000) {
    let elapsed = 0;
    const interval = setInterval(() => {
      elapsed += delay;
      if (elapsed > max || (!this.listening && this.activeSinks.size === 0)) {
        clearInterval(interval);
        cb();
      }
    }, delay);
  }
}
