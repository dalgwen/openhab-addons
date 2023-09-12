import { AudioSink } from "./audio-sink";
import { AudioSource } from "./audio-source";
import { WorkerInCmd, RustpotterOptions, MediaStateCmd, WorkerOutCmd, WorkerOutCmdType, ConfigureSpeakerCmd, MediaCommandCmd } from "./io-types";
import { MessageACKManager } from "./message-ack-manager";
import { RustpotterConfig, RustpotterService, ScoreMode, VADMode } from "rustpotter-worklet";

export interface IOEventListeners {
  onConnected?: (io: IOMain) => void;
  onDisconnected?: (io: IOMain) => void;
  onStartListening?: (io: IOMain) => void;
  onStopListening?: (io: IOMain) => void;
  onStartSpeaking?: (io: IOMain) => void;
  onStopSpeaking?: (io: IOMain) => void;
  onConfigured?: (config: ConfigureSpeakerCmd) => void;
  onMediaCommand?: (mediaCmd: MediaCommandCmd) => void;
  onMessage?: (message: string, type: 'info' | 'error', ms?: number) => (() => void);
}

export class IOMain {
  private online: boolean = false;
  private accessToken: string | null = null;
  private messageACKs = new MessageACKManager("main");
  // audio source
  private audioContext: AudioContext | null = null;
  private audioSource: AudioSource | null = null;
  private sourceVolume: number = 50;
  private worker: Worker | null = null;
  private micStreaming = false;
  private listening: boolean = false;
  private listenPortACK?: number;
  // audio sink
  private activeSinks = new Map<string, AudioSink>();
  private sinkVolume: number = 100;
  // keyword spotting
  private serverSpotting: boolean = false;
  private rustpotter: RustpotterService | null = null;
  private currentWakeword: string | null = null;
  private rustpotterAudioNode: AudioNode | null = null;

  constructor(private ohUrl: string, private callbacks: IOEventListeners = {}) { }
  public isOnline() {
    return this.online;
  }
  public isListening() {
    return this.listening;
  }
  public isSpeaking() {
    return this.listening;
  }
  private startVoiceAudioContext(customSampleRate?: number) {
    if (!this.audioContext) {
      let options: AudioContextOptions = {};
      if (customSampleRate) {
        options.sampleRate = customSampleRate;
      }
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
  private getAudioSource(): AudioSource {
    if (!this.audioSource) {
      throw new Error('AudioSource not initialized');
    }
    return this.audioSource;
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
  private async setupRustpotter(wakeword: string, options: RustpotterOptions): Promise<RustpotterService> {
    if (!this.rustpotter) {
      console.debug("main: starting rustpotter");
      const wasmModuleUrl = new URL('../../node_modules/rustpotter-worklet/dist/rustpotter_wasm_bg.wasm', import.meta.url);
      const workletModuleUrl = new URL('../../node_modules/rustpotter-worklet/dist/rustpotter-worklet.js', import.meta.url);
      const workerModuleUrl = new URL('../../node_modules/rustpotter-worklet/dist/rustpotter-worker.js', import.meta.url);
      const sampleRate = this.getVoiceAudioContext().sampleRate;
      this.rustpotter = await RustpotterService.new(
        sampleRate,
        {
          workletPath: workletModuleUrl.href,
          workerPath: workerModuleUrl.href,
          wasmPath: wasmModuleUrl.href,
        },
        this.getRustpotterConfig(options),
      );
      this.rustpotter.onDetection(detection => {
        console.debug('main: wakeword detected', detection);
        this.sendSpot();
      });
    } else {
      console.debug("main: rustpotter already started, updating config");
      await this.rustpotter.updateConfig(this.getRustpotterConfig(options))
    }
    if (wakeword !== this.currentWakeword) {
      console.debug(`main: downloading and adding rustpotter wakeword '${wakeword}'`);
      let headers: HeadersInit = {};
      if (this.accessToken?.length) {
        headers["Authorization"] = `Bearer ${this.accessToken}`;
      }
      await this.rustpotter.removeWakeword("w");
      await this.rustpotter.addWakewordByPath("w", `${await this.ohUrl}/rest/habspeaker/rustpotter/${encodeURIComponent(wakeword)}`, headers);
      this.currentWakeword = wakeword;
    } else {
      console.debug("main: rustpotter wakeword already loaded");
    }
    return this.rustpotter;
  }
  private async teardownRustpotter() {
    if (this.rustpotter) {
      console.debug("main: teardown rustpotter service");
      await this.rustpotter.close();
      this.rustpotter = null;
      this.currentWakeword = null;
    }
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
      if (this.rustpotterAudioNode) {
        processors.unshift(this.rustpotterAudioNode)
      }
      this.getAudioSource().start(...processors).catch((err) => console.error(err));
    } else {
      console.warn("main: trying to start microphone streaming but it's already started!");
    }
  }
  private stopMicStreaming() {
    if (this.micStreaming) {
      console.debug("stopping microphone audio streaming");
      this.micStreaming = false;
      if (this.rustpotterAudioNode) {
        // keep keyword spotting node connected
        this.audioSource?.start(this.rustpotterAudioNode).catch((err) => console.error(err));
      } else {
        // stop audio node
        this.audioSource?.stop();
      }
    } else {
      console.warn("main: trying to stop microphone streaming but it's already stopped!");
    }
  };
  private async killMicProcessors() {
    this.audioSource?.stop();
    if (this.rustpotterAudioNode) {
      this.rustpotterAudioNode = null;
      await this.rustpotter?.disposeProcessorNode();
    }
  };

  // worker setup
  private handleWorkerMessage(command: WorkerOutCmd, data: WorkerOutCmdType<any>) {
    switch (command) {
      case WorkerOutCmd.CONFIGURE:
        const speakerConfig = data as WorkerOutCmdType<typeof command>;
        (async () => {
          const audioContext = this.getVoiceAudioContext();
          const closeMsg = this.callbacks.onMessage?.("Resuming audio context, click to continue", "info");
          await audioContext.resume();
          closeMsg?.();
          await AudioSink.configure(audioContext, speakerConfig.useAudioElement);
          this.audioSource?.setVolume(speakerConfig.sourceVolume ?? this.sourceVolume);
          if (speakerConfig.sinkVolume != null) {
            this.sinkVolume = speakerConfig.sinkVolume;
          }
          debugger
          this.serverSpotting = false;
          switch (speakerConfig.spotMode) {
            case "server":
              await this.teardownRustpotter();
              this.serverSpotting = true;
              this.callbacks.onMessage?.("Running keyword spotting against the server.", "info", 5000);
              break;
            case "rustpotter_web":
              if (speakerConfig.spotConfig?.keyword) {
                this.callbacks.onMessage?.("Running keyword spotting locally.", "info", 5000);
                try {
                  const rustpotter = await this.setupRustpotter(speakerConfig.spotConfig.keyword, speakerConfig.spotConfig);
                  console.debug("main: creating rustpotter audio worklet");
                  this.rustpotterAudioNode = await rustpotter.getProcessorNode(this.getVoiceAudioContext());
                  console.debug("main: connecting rustpotter audio worklet");
                  await this.audioSource?.start(this.rustpotterAudioNode);
                } catch (error) {
                  console.error("Unable to start local ks processor", error);
                  this.callbacks.onMessage?.("Error starting local keyword spotter.", "info", 5000);
                }
              } else {
                console.warn("main: Missed spotConfig configuration");
                this.callbacks.onMessage?.("Error starting local keyword spotter.", "error", 5000);
              }
              break;
            case "none":
            default:
              this.callbacks.onMessage?.("No keyword spotter, click the widget to trigger the dialog.", "info", 5000);
              await this.teardownRustpotter();
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
        this.callbacks.onConnected?.(this);
        this.callbacks.onMessage?.("Speaker connected.", "info", 2000);
        if (this.serverSpotting) {
          console.debug("remote spot enabled, starting mic streaming");
          this.startMicStreaming();
        }
        break;
      case WorkerOutCmd.OFFLINE:
        this.callbacks.onMessage?.("Speaker disconnected, trying to reconnect.", "error", 2000);
        this.sinkVolume = 0;
        this.serverSpotting = false;
        this.killMicProcessors();
        if (this.listening) {
          this.listening = false;
          this.callbacks.onStopListening?.(this);
        }
        if (this.online) {
          this.online = false;
          this.callbacks.onDisconnected?.(this);
        }
        if (this.listenPortACK) {
          this.messageACKs.abortACK(this.listenPortACK);
          this.listenPortACK = undefined;
        }
        break;
      case WorkerOutCmd.START_SINK:
        const startSinkCmd = data as WorkerOutCmdType<typeof command>;
        const sink = new AudioSink(startSinkCmd.id, startSinkCmd.channels, this.sinkVolume);
        const sinkPortCmd = { cmd: WorkerInCmd.SINK_PORT, id: sink.getId(), port: sink.getMessagePort() };
        this.worker?.postMessage(sinkPortCmd, [sinkPortCmd.port]);
        this.activeSinks.set(sink.getId(), sink);
        const startSpeaking = this.activeSinks.size === 1;
        sink.start().then(() => {
          if (startSpeaking) {
            this.callbacks.onStartSpeaking?.(this);
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
            this.callbacks.onStopSpeaking?.(this);
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
          this.callbacks.onStartListening?.(this);
        }
        if (!this.serverSpotting) {
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
          this.callbacks.onStopListening?.(this);
        }
        if (!this.serverSpotting) {
          this.stopMicStreaming();
        }
        break;
      case WorkerOutCmd.SINK_VOLUME:
        const { value: sinkVolume } = data as WorkerOutCmdType<typeof command>;
        this.callbacks.onMessage?.(`Sink volume: ${sinkVolume}.`, "info", 1000);
        this.sinkVolume = sinkVolume;
        this.activeSinks.forEach(sink => sink.setVolume(this.sinkVolume));
        break;
      case WorkerOutCmd.SOURCE_VOLUME:
        const { value: sourceVolume } = data as WorkerOutCmdType<typeof command>;
        this.callbacks.onMessage?.(`Source volume: ${sourceVolume}.`, "info", 1000);
        this.sourceVolume = sourceVolume;
        this.audioSource?.setVolume(sourceVolume);
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
  private getRustpotterConfig(options: RustpotterOptions): RustpotterConfig {
    const scoreMode = (ScoreMode[options.scoreMode as any] as any) ?? ScoreMode.max;
    const vadMode = !!(options.vadMode?.length) ? (VADMode[options.vadMode as any] as any) ?? null : null;
    return {
      averagedThreshold: options.averagedThreshold,
      threshold: options.threshold,
      minScores: options.minScores,
      eager: options.eager,
      minGain: options.minGain,
      maxGain: options.maxGain,
      bandPassEnabled: options.bandPassEnabled,
      bandPassLowCutoff: options.bandPassLowCutoff,
      bandPassHighCutoff: options.bandPassHighCutoff,
      bandSize: options.bandSize,
      scoreRef: options.scoreRef,
      gainNormalizerEnabled: options.gainNormalizerEnabled,
      gainRef: options.gainRef,
      scoreMode,
      vadMode,
    };
  }
  public async initialize(speakerId: string, token: string | null, customSampleRate?: number) {
    this.startVoiceAudioContext(customSampleRate);
    const audioContext = this.getVoiceAudioContext();
    await audioContext.resume();
    await AudioSource.configure(audioContext);
    this.audioSource = new AudioSource(50);
    await this.audioSource.resume();

    // microphone stream checker, to keep the stream alive on undetected disconnections  
    setInterval(() => {
      if (this.audioSource?.isSuspended()) {
        this.audioSource
          .resume()
          .catch(err => console.error("Unable to resume audio context", err))
      }
    }, 10000);
    document.addEventListener("visibilitychange", () => {
      if (!document.hidden && this.audioSource?.isSuspended()) {
        this.audioSource.resume()
          .catch(err => console.error("Unable to resume audio context", err))
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
