import { ReentrantLock } from "reentrant-lock";
import { AudioSink } from "./audio/audio-sink";
import { AudioSource } from "./audio/audio-source";
import { WorkerInCmd, RustpotterOptions, MediaStateCmd, WorkerOutCmd, WorkerOutCmdType, ConfigureSpeakerCmd, MediaCommandCmd } from "./io-types";
import { RustpotterConfig, RustpotterService, ScoreMode, VADMode } from "rustpotter-worklet";

export interface IOEventListeners {
  onRunningChange?: (io: IOMain) => void;
  onListeningChange?: (io: IOMain) => void;
  onSpeakingChange?: (io: IOMain) => void;
  onConfigured?: (config: ConfigureSpeakerCmd) => void;
  onMediaCommand?: (mediaCmd: MediaCommandCmd) => void;
  onMessage?: (message: string, type: 'info' | 'error', ms?: number) => (() => void);
}

export class IOMain {
  private online: boolean = false;
  private accessToken: string | null = null;
  // voice audio context
  private audioContext: AudioContext | null = null;
  /// audio source
  private audioSource: AudioSource | null = null;
  private speakerStateLock = new ReentrantLock();
  private sourceVolume: number = 50;
  private micStreaming = false;
  private listening: boolean = false;
  private resolveSourcePort?: () => void;
  ///
  /// audio sink
  private activeSinks = new Map<string, AudioSink>();
  private sinkVolume: number = 100;
  ///
  /// keyword spotting
  private serverSpotting: boolean = false;
  private rustpotter: RustpotterService | null = null;
  private currentWakeword: string | null = null;
  private rustpotterAudioNode: AudioNode | null = null;
  ///
  // Worker to handle audio resampling, re-encoding and transmission,
  private worker: Worker = new Worker(new URL("./io-worker.ts", import.meta.url), {
    name: "habspeaker-audio-worker",
    type: "module",
  });

  constructor(private ohUrl: string, private events: IOEventListeners = {}) { }
  public isRunning() {
    return this.online && this.audioContext?.state === 'running';
  }
  public isListening() {
    return this.listening;
  }
  public isSpeaking() {
    return this.activeSinks.size > 0;
  }
  /**
   * Initializes the audio context used for the sink and source.
   * @param customSampleRate Custom sample rate to use, not functional in some browsers.
   */
  private startVoiceAudioContext(customSampleRate?: number) {
    if (!this.audioContext) {
      const options: AudioContextOptions = {};
      if (customSampleRate) {
        options.sampleRate = customSampleRate;
      }
      this.audioContext = new AudioContext(options);
      console.debug(`main: Created audio context with sample rate ${this.audioContext.sampleRate}`);
    }
  }
  /**
   * Returns the audio context asserting it's defined.
   * 
   * @returns the shared audio context.
   */
  private getVoiceAudioContext(): AudioContext {
    if (!this.audioContext) {
      throw new Error('AudioContext not initialized');
    }
    return this.audioContext;
  }
  /**
   * Returns the audio context asserting it's initialized.
   * 
   * @returns the audio source implementation.
   */
  private getAudioSource(): AudioSource {
    if (!this.audioSource) {
      throw new Error('AudioSource not initialized');
    }
    return this.audioSource;
  }
  /**
   * Returns an audio processor node connected to the audio worker, so the audio gets converted and streamed to the server. 
  */
  private async getWorkerAudioProcessor() {
    const audioContext = this.getVoiceAudioContext();
    const _webSocketWorkletNode = new AudioWorkletNode(audioContext, 'habspeaker-source-worklet', { numberOfInputs: 1, numberOfOutputs: 0, channelCount: 1, channelCountMode: 'explicit' });
    const portPromise = new Promise<void>(resolve => this.resolveSourcePort = resolve);
    const command = { cmd: WorkerInCmd.SOURCE_PORT, port: _webSocketWorkletNode.port };
    this.worker?.postMessage(command, [command.port]);
    await portPromise;
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
      const headers: HeadersInit = {};
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
  private postToWorker(cmd: string, args: { [key: string]: unknown } = {}) {
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
      console.info("main: sending spot event");
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
  public setAuthToken(token: string | null) {
    this.accessToken = token;
    if (token && this.worker) {
      this.postToWorker(WorkerInCmd.TOKEN_RENEW, { token });
    }
  }
  /**
   * Connect the worker input audio node to the audio context media stream output,
   * it keeps the keyword spotter input audio node connected if exists.
   */
  private async startMicStreaming() {
    if (!this.micStreaming) {
      this.micStreaming = true;
      console.debug("starting microphone audio streaming");
      const processors: AudioNode[] = [await this.getWorkerAudioProcessor()];
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
  /**
   * Disconnect the worker input audio node from the audio context media stream output,
   * it keeps the keyword spotter input audio node connected if exists.
   */
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
  }
  /**
   * Clean up function, tries to dispose worker and keyword spotter input audio nodes.
   */
  private async killMicProcessors() {
    this.audioSource?.stop();
    if (this.rustpotterAudioNode) {
      this.rustpotterAudioNode = null;
      await this.rustpotter?.disposeProcessorNode();
    }
  }

  /**
   * Handle messages from the websocket.
   * 
   * @param command the command name.
   * @param data the command data.
   */
  private handleWorkerMessage(command: WorkerOutCmd, data: WorkerOutCmdType<WorkerOutCmd>) {
    switch (command) {
      case WorkerOutCmd.CONFIGURE: {
        const speakerConfig = data as WorkerOutCmdType<typeof command>;
        this.updateConfiguration(speakerConfig)
          .catch(err => console.error("io-worker: error updating speaker configuration", err))
          .finally(() => {
            this.events.onConfigured?.(speakerConfig);
            this.postToWorker(WorkerInCmd.CONFIGURED);
          });
        break;
      }
      case WorkerOutCmd.SOURCE_READY:
        this.resolveSourcePort?.();
        this.resolveSourcePort = undefined;
        break;
      case WorkerOutCmd.INITIALIZED:
        this.online = true;
        this.events.onRunningChange?.(this);
        this.events.onMessage?.("Speaker connected", "info", 2000);
        if (this.serverSpotting) {
          console.debug("remote spot enabled, starting mic streaming");
          this.startMicStreaming();
        }
        break;
      case WorkerOutCmd.OFFLINE:
        this.events.onMessage?.("Speaker disconnected, trying to reconnect", "error", 2000);
        this.sinkVolume = 0;
        this.serverSpotting = false;
        this.resolveSourcePort?.();
        this.resolveSourcePort = undefined;
        this.killMicProcessors();
        if (this.listening) {
          this.listening = false;
          this.events.onListeningChange?.(this);
        }
        if (this.online) {
          this.online = false;
          this.events.onRunningChange?.(this);
        }
        break;
      case WorkerOutCmd.START_SINK: {
        const startSinkCmd = data as WorkerOutCmdType<typeof command>;
        const sink = new AudioSink(startSinkCmd.id, startSinkCmd.channels, this.sinkVolume);
        const sinkPortCmd = { cmd: WorkerInCmd.SINK_PORT, id: sink.getId(), port: sink.getMessagePort() };
        this.worker?.postMessage(sinkPortCmd, [sinkPortCmd.port]);
        this.activeSinks.set(sink.getId(), sink);
        const startSpeaking = this.activeSinks.size === 1;
        console.debug(`main: starting sink ${sink.getId()}`);
        sink.start().then(() => {
          if (startSpeaking) {
            this.events.onSpeakingChange?.(this);
          }
        }).catch(err => console.error(err));
        break;
      }
      case WorkerOutCmd.STOP_SINK: {
        const stopSinkCmd = data as WorkerOutCmdType<typeof command>;
        const activeSink = this.activeSinks.get(stopSinkCmd.id);
        if (activeSink) {
          console.debug(`main: stopping sink ${stopSinkCmd.id}`);
          this.activeSinks.delete(stopSinkCmd.id);
          activeSink.close();
          if (this.activeSinks.size === 0) {
            this.events.onSpeakingChange?.(this);
          }
        } else {
          console.error("main: unable to stop sink, not found ", stopSinkCmd.id);
        }
        break;
      }
      case WorkerOutCmd.START_LISTENING:
        if (!this.online) {
          console.debug("main: ignoring start listening message before init");
          return;
        }
        if (!this.listening) {
          this.listening = true;
          this.events.onListeningChange?.(this);
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
          this.events.onListeningChange?.(this);
        }
        if (!this.serverSpotting) {
          this.stopMicStreaming();
        }
        break;
      case WorkerOutCmd.SINK_VOLUME: {
        const { value: sinkVolume } = data as WorkerOutCmdType<typeof command>;
        this.events.onMessage?.(`Sink volume: ${sinkVolume}`, "info", 1000);
        this.sinkVolume = sinkVolume;
        this.activeSinks.forEach(sink => sink.setVolume(this.sinkVolume));
        break;
      }
      case WorkerOutCmd.SOURCE_VOLUME: {
        const { value: sourceVolume } = data as WorkerOutCmdType<typeof command>;
        this.events.onMessage?.(`Source volume: ${sourceVolume}`, "info", 1000);
        this.sourceVolume = sourceVolume;
        this.getAudioSource().setVolume(sourceVolume);
        break;
      }
      case WorkerOutCmd.MEDIA_COMMAND: {
        const mediaCommandData = data as WorkerOutCmdType<typeof command>;
        this.events.onMediaCommand?.(mediaCommandData);
        break;
      }
      default:
        console.error(`main: Unknown worker command ${command}`);
    }
  }
  /**
   * Handles the speaker configuration message.
   * 
   * @param speakerConfig The speaker configuration instructed by the server.
   */
  private async updateConfiguration(speakerConfig: ConfigureSpeakerCmd) {
    const audioContext = this.getVoiceAudioContext();
    const resumeAudioContext = () => audioContext.resume();
    const closeMsg = this.events.onMessage?.("Resuming audio context, click to continue", "info");
    document.addEventListener("click", resumeAudioContext);
    await this.getAudioSource().resume();
    document.removeEventListener("click", resumeAudioContext);
    closeMsg?.();
    document.removeEventListener("visibilitychange", this.handleSuspendOnHidden);
    if (speakerConfig.suspendOnHide) {
      document.addEventListener("visibilitychange", this.handleSuspendOnHidden);
    }
    await AudioSink.configure(audioContext, speakerConfig.useAudioElement);
    this.getAudioSource().setVolume(speakerConfig.sourceVolume ?? this.sourceVolume);
    this.sinkVolume = speakerConfig.sinkVolume ?? this.sinkVolume;
    this.serverSpotting = false;
    console.debug(`main: configured spot mode ${speakerConfig.spotMode}`);
    switch (speakerConfig.spotMode?.toLocaleLowerCase()) {
      case "server":
        await this.teardownRustpotter();
        this.serverSpotting = true;
        this.events.onMessage?.("Running keyword spotting against the server", "info", 5000);
        break;
      case "rustpotter_web":
        if (speakerConfig.spotConfig?.wakeword) {
          this.events.onMessage?.("Running keyword spotting locally", "info", 5000);
          try {
            const rustpotter = await this.setupRustpotter(speakerConfig.spotConfig.wakeword, speakerConfig.spotConfig);
            console.debug("main: creating rustpotter audio worklet");
            this.rustpotterAudioNode = await rustpotter.getProcessorNode(this.getVoiceAudioContext());
            console.debug("main: connecting rustpotter audio worklet");
            await this.getAudioSource().start(this.rustpotterAudioNode);
          } catch (error) {
            console.error("Unable to start local ks processor", error);
            this.events.onMessage?.("Error starting local keyword spotter", "info", 5000);
          }
        } else {
          console.warn("main: Missed spotConfig configuration");
          this.events.onMessage?.("Error starting local keyword spotter", "error", 5000);
        }
        break;
      case "none":
      default:
        this.events.onMessage?.("No keyword spotter, click the widget to trigger the dialog", "info", 5000);
        await this.teardownRustpotter();
        break;
    }
  }
  /**
   * Parse the speaker rustpotter options into a valid rustpotter config.
   * 
   * @param options speaker options for rustpotter local execution.
   * @returns a correct rustpotter config.
   */
  private getRustpotterConfig(options: RustpotterOptions): RustpotterConfig {
    const scoreMode = (ScoreMode as unknown as { [key: string]: ScoreMode })[options.scoreMode] ?? ScoreMode.max;
    const vadMode = options.vadMode?.length ? (VADMode as unknown as { [key: string]: VADMode })[options.vadMode] : undefined;
    return {
      averagedThreshold: options.avgThreshold,
      threshold: options.threshold,
      minScores: options.minScores,
      eager: options.eager,
      minGain: options.minGain,
      maxGain: options.maxGain,
      bandPassEnabled: options.bandPass,
      bandPassLowCutoff: options.lowCutoff,
      bandPassHighCutoff: options.highCutoff,
      bandSize: options.bandSize,
      scoreRef: options.scoreRef,
      gainNormalizerEnabled: options.gainNormalizer,
      gainRef: options.gainRef,
      scoreMode,
      vadMode,
    };
  }
  private sourceCheckIntervalRef?: ReturnType<typeof setInterval>;
  private startSourceCheckInterval() {
    this.stopSourceCheckInterval();
    this.sourceCheckIntervalRef = setInterval(() => this.getAudioSource()
      .resume()
      .catch(err => console.error("Unable to resume audio source", err)),
      5000,
    );
  }
  private stopSourceCheckInterval() {
    if (this.sourceCheckIntervalRef) {
      clearInterval(this.sourceCheckIntervalRef);
      this.sourceCheckIntervalRef = undefined;
    }
  }
  private handleSuspendOnHidden = () => {
    if (!document.hidden) {
      this.speakerStateLock.lock(async () => {
        await this.getVoiceAudioContext().resume().catch(err => console.error("Error resuming audio context", err));
        this.startSourceCheckInterval();
        this.postToWorker(WorkerInCmd.RESUME);
      });
    } else {
      this.speakerStateLock.lock(async () => {
        this.stopSourceCheckInterval();
        await this.getVoiceAudioContext().suspend().catch(err => console.error("Error suspending audio context", err));
        this.postToWorker(WorkerInCmd.SUSPEND);
      });
    }
  };
  /**
   * Initializes the workers instance.
   * 
   * @param speakerId the speaker identifier used by the server.
   * @param customSampleRate Custom sample rate for the voice context, non functional in some browsers.
   */
  public async initialize(speakerId: string, customSampleRate?: number) {
    this.events.onMessage?.("Connecting speaker...", "info", 500);
    this.startVoiceAudioContext(customSampleRate);
    const audioContext = this.getVoiceAudioContext();
    audioContext.onstatechange = () => {
      console.debug(`main: Audio context state '${audioContext.state}'`);
      this.events.onRunningChange?.(this);
    };
    await audioContext.resume();
    await AudioSource.configure(audioContext);
    this.audioSource = new AudioSource(50);
    await this.audioSource.resume();
    this.startSourceCheckInterval();
    try {
      this.worker.onmessage = (ev: MessageEvent<unknown>) => {
        if (import.meta.env.DEV) {
          console.debug("worker => main:", ev.data);
        }
        this.handleWorkerMessage((ev.data as unknown as { cmd: WorkerOutCmd }).cmd, ev.data as unknown as WorkerOutCmdType<WorkerOutCmd>);
      };
      this.worker.onerror = (err) => {
        console.error("io-main: Worker error.", err);
      };
      this.worker?.postMessage({
        cmd: WorkerInCmd.INITIALIZE,
        id: speakerId,
        token: this.accessToken,
        sampleRate: this.getVoiceAudioContext().sampleRate,
        ohUrl: this.ohUrl,
      });
    } catch (error) {
      this.events.onMessage?.("Unable to start WebWorker, try reloading the page", "error", 2000);
      throw error;
    }
  }
}
