# HAB Speaker

<img src="https://i.imgur.com/k9ft6n9.gif" title="Hab Speaker Gif" width="500"/>

## Requirements:

* You should have configured your default <b>speech-to-text, text-to-speech, voice and interpreter</b> in the openHAB Voice settings (Main UI), and have these services correctly setup.
* Most browser security policies <b>requires a secure context</b> to use its record capabilities, so you should access the page using 'https' (or disable this browser security policy, which is not recommended).

## Limitations:

* At the moment of writing it <b>does not work with openHAB cloud instances</b>, meaning you can not use it through the myopenhab.org web page.


## Description

HAB Speaker is a project designed to facilitate the use of the [openHAB](https://openhab.org) dialog processing capabilities.

Through a websocket connection this web interface registers a sink and source in your openHAB instance that can be used like any other ones.
It also registers and persistent dialog processor in the server for those components.

The speakers can be discovered as things using the discovery service.

It is another step to have a full, open source, integrated voice assistant for your openHAB smart home.

Once installed the web client will be listed on right panel of the main ui (home screen), or you can go to it by navigating to '<your openHAB url>/habspeaker'.

## Service Settings

You can edit this settings for the service in the main ui **Settings / Other Services - HAB Speaker**:

* **Secure** - Require user credentials to use the speaker (the ui will redirect you to the login page when needed).

## Local Settings:

You can access a basic form by clicking the 'Settings' button at the top right of the speaker ui where you can configure the following:

* **Id** - This is the id that identifies your speaker against the server.

These settings are stored on your browser local storage. 

## Thing Configurations

| ThingTypeID | description                      |
|-------------|----------------------------------|
| speaker     | A connected speaker (the web ui) |

| Config        |  Type   | Group | Description                                                                |
|---------------|---------|-------|----------------------------------------------------------------------------|
| sinkVolume    | String  | audio | Default sink volume                                                        |
| sinkStereo    | Boolean | audio | Use dual channel audio                                                     |
| stt           | String  | voice | The text-to-speech service to use, leave empty to use the default.         |
| tts           | String  | voice | The speech-to-text service to use, leave empty to use the default.         |
| voice         | String  | voice | The voice to use if no voice is specified, leave empty to use the default. |
| hli           | String  | voice | The human language interpreter to use, leave empty to use the default.     |
| ks            | String  | voice | Enables keyword spotting using the specified service.                      |
| listeningItem | String  | voice | If provided, the item will be switched on during the voice recognition.    |

## Thing Channels

| Channel     |  Type  | description                  |
|-------------|--------|------------------------------|
| sinkVolume  | Dimmer | Controls the sink volume     |

## Thing Discovery

The discovery service will register all the connected speakers.

## Basic Usage:

* Remember to have installed and setup the default <b>speech-to-text, text-to-speech and interpreter</b> services using the main ui.
* As most browsers requires user interaction to access its audio capabilities, you should click at the speaker panel at first. It will then ask for user permissions and show a loading animation until it's ready.
* When the speaker is ready the registered sink and source will be visible in openHAB (audio settings section of the Main UI or though the console commands 'audio sinks' and 'audio sources').
* The speaker icon circle displays an animation whenever it's waiting for you to talk.
* The speaker icon dots part displays an animation while it's playing audio.
* By clicking on the speaker icon a single shot audio dialog processing will start. It uses the registered audio components (sink/source) and the default processing services configured on the openHAB voice settings.
* At this point you can discover the speaker using the main ui to add it as a thing in openHAB.

## Audio Component Details:

The audio sink registered supports the following audio format: WAV PCM-SIGNED 16000hz 16-bit mono (single channel).
It's supported by most if not all the speech-to-text services available at openHAB. 

The audio source registered supports any wav audio format as it will try to convert it to the appropriate format (PCM-SIGNED 16-bit mono (or stereo) at required sample rate).
