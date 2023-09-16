# HABSpeaker

<img src="https://i.imgur.com/k9ft6n9.gif" title="Hab Speaker Gif" width="500"/>

## Requirements:

* You should have configured your default <b>speech-to-text, text-to-speech, voice and interpreter</b> in the openHAB Voice settings (Main UI), and have these services correctly setup.
* Most browser security policies <b>requires a secure context</b> to use its record capabilities, so you should access the page using 'https' (or disable this browser security policy, which is not recommended).

## Limitations:

* At the moment of writing it <b>does not work with openHAB cloud instances</b>, meaning you can not use it through the myopenhab.org web page.


## Description

HABSpeaker is a project designed to facilitate the use of the [openHAB](https://openhab.org) dialog processing capabilities.

Through a websocket connection this web interface registers a sink and source in your openHAB instance that can be used like any other ones.
It also registers and persistent dialog processor in the server for those components.

The speakers can be discovered as things using the discovery service.

It is another step to have a full, open source, integrated voice assistant for your openHAB smart home.

Once installed the web client will be listed on right panel of the main ui (home screen), or you can go to it by navigating to '<your openHAB url>/habspeaker'.

## Local Settings:

HABSpeaker needs some local settings in order to work: 

* **SpeakerId** - This is the id that identifies your speaker against the server, will be part of the thing id.
* **OH Url** - Your openHAB server url. (only displays on desktop/mobile)
* **OH Token** - An api token that grant access to your server. (only displays on desktop/mobile)

You can configure this settings in the initial form you will be displayed if those are not setup.

When running as a web page these settings are stored on your browser local storage.

When running as a desktop app these settings are stored on '$HOME/.HABSpeaker/settings.json'.

When running as a mobile app these settings are stored on its shared preferences. (not ready).

## General Settings

You can edit these service options using the main ui by accessing the settings in the HABSpeaker addon page.
There are different sections:

### Voice Control

Define the phrases you can use to interact with the speaker. For the phrases you can add multiple options separated by ';'. Leave a phrase empty to disable it

Those are:

* **Command Send Message** - Message to say on the speaker on command success. (Default: done)
* **Start Drop In Phrase** - Phrase to start drop-in to another speaker.
* **Stop Drop In Phrase** - Phrase to stop drop-in on the current speaker.
* **Resume Media Phrase** - Phrase to resume media.
* **Pause Media Phrase** - Phrase to pause media.
* **Stop Media Phrase** - Phrase to pause media.
* **Decrease Media Volume Phrase** - Phrase to decrease the media volume by the configured step.
* **Increase Media Volume Phrase** - Phrase to increase the media volume by the configured step.
* **Media Volume Step** - Volume step used by the increase/decrease media volume phrases.
* **Fast-Forward Media Progress Phrase** - Phrase to fast-forward the media progress.
* **Rewind Media Progress Phrase** - Phrase to rewind the media progress.
* **Next Media Phrase** - Phrase template to go to the next media item.
* **Previous Media Phrase** - Phrase template to go to the previous media item.
* **Listen on Web Phrase** - Phrase to listen a song on the current speaker (Example: 'play $*').
* **Watch on Web Phrase** - Phrase to watch a video on the current speaker (Example: 'watch $*').

## Thing Configurations

| ThingTypeID | description                      |
|-------------|----------------------------------|
| speaker     | A connected speaker (the web ui) |

| Config                       | Type    | Group          | Advanced | Description                                                                  |
|------------------------------|---------|----------------|-----------------------------------------------------------------------------------------|
| sinkVolume                   | String  | audio          | false     | Default sink volume.                                                         |
| sourceVolume                 | String  | audio          | false     | Default sink volume.                                                         |
| sinkStereo                   | Boolean | audio          | true      | Use dual channel audio.                                                      |
| sampleRate                   | Number  | audio          | true      | Sample rate used for transmission, set to 'client' to use the device one.    |
| clientResampleMode           | String  | audio          | true      | Resample mode used by the client when resampling is needed.                  |
| changeSampleRate             | Boolean | audio          | true      | Set a custom audio context sample rate (not work on safari).                 |
| useAudioElement              | Boolean | audio          | true      | Use an audio element to render the sink audio. (Affects echo cancellation)   |
| screenSaverTime              | String  | device         | false     | Seconds to activate screen saver (0 for disabled).                           |
| dimScreen                    | Boolean | device         | false     | Prevent device of going to sleep/block (not available on web).               |
| keepAwake                    | Boolean | device         | false     | Lower the screen brightness while the screen saver is enabled.               |
| stt                          | String  | voice          | false     | The text-to-speech service to use, leave empty to use the default.           |
| tts                          | String  | voice          | false     | The speech-to-text service to use, leave empty to use the default.           |
| voice                        | String  | voice          | false     | The voice to use if no voice is specified, leave empty to use the default.   |
| hli                          | String  | voice          | false     | The human language interpreter to use, leave empty to use the default.       |
| ks                           | String  | voice          | false     | Enables keyword spotting using the specified service.                        |
| listeningItem                | String  | voice          | false     | If provided, the item will be switched on during the voice recognition.      |
| rustpotterThreshold          | Decimal | rustpotter_web | false     | Spot detection threshold (only for rustpotter web ks).                       |
| rustpotterAvgThreshold       | Decimal | rustpotter_web | true      | Spot average threshold (0 for disabled) (only for rustpotter web ks).        |
| rustpotterScoreMode          | String  | rustpotter_web | true      | Min detection scores (only for rustpotter web ks).                           |
| rustpotterVADMode            | String  | rustpotter_web | true      | Enables a basic VAD detector (only for rustpotter web ks).                   |
| rustpotterScoreRef           | Decimal | rustpotter_web | true      | Score reference (only for rustpotter web ks) (Advanced).                     |
| rustpotterBandSize           | Number  | rustpotter_web | true      | Band size (only for rustpotter web ks). (Advanced).                          |
| rustpotterGainNormalizer     | Boolean | rustpotter_web | false     | Enabled the gain-normalizer filter (only for rustpotter web ks).             |
| rustpotterMinGain            | Decimal | rustpotter_web | false     | Min gain applied by the gain-normalizer filter (only for rustpotter web ks). |
| rustpotterMaxGain            | Decimal | rustpotter_web | false     | Max gain applied by the gain-normalizer filter (only for rustpotter web ks). |
| rustpotterGainRef            | Decimal | rustpotter_web | false     | The gain-normalizer RMS reference (only for rustpotter web ks).              |
| rustpotterBandPass           | Boolean | rustpotter_web | true      | Enabled the band-pass filter (only for rustpotter web ks).                   |
| rustpotterLowCutoff          | Decimal | rustpotter_web | true      | Low cutoff for the band-pass filter (only for rustpotter web ks).            |
| rustpotterHighCutoff         | Decimal | rustpotter_web | true      | High cutoff for the band-pass filter (only for rustpotter web ks).           |
| primaryColor                 | String  | theme          | false     | Css compatible color value to use as primary. (Default: OpenHAB red)         |
| secondaryColor               | String  | theme          | false     | Css compatible color value to use as secondary. (Default: OpenHAB gay)       |
| tertiaryColor                | String  | theme          | false     | Css compatible color value to use as tertiary. (Default: back)               |
| logoUrl                      | String  | theme          | false     | Image path to use as logo. (Default: OpenHAB logo).                          |

## Thing Channels

| Channel ID           |  Type   | description                                                             |
|----------------------|---------|-------------------------------------------------------------------------|
| sink-volume          | Dimmer  | Controls the sink volume of the speaker.                                |
| source-volume          | Dimmer  | Controls the source volume of the speaker.                              |
| spot                 | Switch  | Starts dialog processing on the speaker.                                |
| drop-in              | String  | Starts an immediate call with other speaker (by id).                    |
| media-current-second | Number  | Current second for the media currently playing, allow seek.             |
| media-total-seconds  | Number  | Total seconds for the media currently playing.                          |
| media-progress       | Dimmer  | Played percentage for the media currently playing, allow seek.          |
| play-audio           | String  | Start playing a audio by url.                                           |
| play-video           | String  | Start playing a video by url.                                           |
| audio-search         | String  | Used to write the searches from the 'listenAudioPhrase' voice command.  |
| video-search         | String  | Used to write the searches from the 'watchVideoPhrase' voice command.   |

## Thing Discovery

All the connected speakers can be automatically discovered using the main ui.

## Basic Usage:

* Remember to have installed and setup the default <b>speech-to-text, text-to-speech and interpreter</b> services using the main ui.
* As most browsers requires user interaction to access its audio capabilities, you should click at the speaker panel at first. It will then ask for user permissions and show a loading animation until it's ready.
* When the speaker is ready the registered sink and source will be visible in openHAB (audio settings section of the Main UI or though the console commands 'audio sinks' and 'audio sources').
* The main button circle displays pulse animation while the audio source is active.
* The main button displays a speaker icon while the audio sink is active.
* By clicking on the speaker icon a single shot audio dialog processing will start. It uses the registered audio components (sink/source) and the default processing services configured on the openHAB voice settings.
* At this point you can discover the speaker using the main ui to add it as a thing in openHAB. (Path: /settings/things/add/habspeaker)

## Browser keyword spotting

### Rustpotter

Note: Uses rustpotter v3.x.x not compatible with 1.x.x model files.

To run keyword spotting on the browser using rustpotter:

* Select 'Rustpotter Web' as 'Keyword Spotter' in the thing configuration 'Voice' section (when configuring in file, set ks thing config to 'habspeaker::rustpotter_web::ks').
* The rustpotter wakeword file should be available under '$OPENHAB_USERDATA/habspeaker/rustpotter' and configured in the "Rustpotter Web" config section of the habspeaker thing in openHAB.

## Media Playback

Once the UI is discovered by openHAB you can play media on it controlling the web player through its channels.

Browsers don't all support the same audio/video formats.

### Setup

You can configure this settings on the first application launch, using a built-it form, so no need to do it manually on the file system.

The app settings json file in the path '$HOME/.HABSpeaker/settings.json' will look like:

```json
{"speakerId": "myspeakerid", "ohUrl": "http://192.168.1.200:8080", "ohToken": "oh.TokenNme.RANDOM"}

```

The 'ohToken' should be a persistent [openHAB API token](https://www.openhab.org/docs/configuration/apitokens.html). This token is not required if an implicit user role is granted, configurable in the OpenHAB 'Api Security' settings.

### Supported Platforms

The electron application should work on:

* macOS (arm64, x64)
* Linux (arm64, x64)
* Window (x64)

Note: Build is tested on all the environments but the application has only been tested on macOS and Windows, needs testing but everything seems to work.

### App Installer Build

You can find some scripts to help you build the application on the 'web/tools' folder. In requires you to have installed the appropiate dependencies (nodejs and rust | docker with buildx).

### App Installer Downloads

App installers can be found [here](https://github.com/habspeaker/habspeaker-builds/releases).

## Audio Component Details:

The audio source registered supports the following audio format: WAV PCM-SIGNED 16000hz 16-bit mono.
It's supported by most if not all the speech-to-text services available at openHAB. 

The audio sink registered supports any wav or mp3 audio as it will try to convert it to the appropriate format.
