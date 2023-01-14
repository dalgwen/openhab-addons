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

## General Settings

You can edit this settings for the service in the main ui **Settings / Other Services - HAB Speaker** there are different sections:

### Service Settings

Service related configurations:

* **Secure** - Require user credentials to use the speaker (the ui will redirect you to the login page when needed).

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
* **Listen on Spotify Phrase** - Phrase to listen a spotify song on the current speaker (Example: 'play $* on spotify').
* **Watch on YouTube Phrase** - Phrase to watch a YouTube video on the current speaker (Example: 'watch $* on youtube').


### Media Providers

Configure required credentials for the supported media providers:

* **Spotify Client Id** - Client ID for a Spotify app. (Creating a Spotify app requires a paid account) (Required for Spotify integration).
* **Youtube API Key** -API key for a Google Cloud application with API 'YouTube Data API v3' enabled. (Required for the YouTube search functionality).

## Local Settings:

You can access a basic form by clicking the 'Settings' button at the top right of the speaker ui where you can configure the following:

* **Id** - This is the id that identifies your speaker against the server.

These settings are stored on your browser local storage. 

## Thing Configurations

| ThingTypeID | description                      |
|-------------|----------------------------------|
| speaker     | A connected speaker (the web ui) |

| Config          |  Type   | Group | Description                                                                |
|-----------------|---------|-------|----------------------------------------------------------------------------|
| sinkVolume      | String  | audio | Default sink volume.                                                       |
| sinkStereo      | Boolean | audio | Use dual channel audio.                                                    |
| stt             | String  | voice | The text-to-speech service to use, leave empty to use the default.         |
| tts             | String  | voice | The speech-to-text service to use, leave empty to use the default.         |
| voice           | String  | voice | The voice to use if no voice is specified, leave empty to use the default. |
| hli             | String  | voice | The human language interpreter to use, leave empty to use the default.     |
| ks              | String  | voice | Enables keyword spotting using the specified service.                      |
| listeningItem   | String  | voice | If provided, the item will be switched on during the voice recognition.    |
| screenSaverTime | String  | ui    | Seconds to activate screen saver (0 for disabled).                         |

## Thing Channels

| Channel ID           |  Type   | description                                                                                      |
|----------------------|---------|--------------------------------------------------------------------------------------------------|
| sink-volume          | Dimmer  | Controls the sink volume of the speaker.                                                         |
| spot                 | Switch  | Starts dialog processing on the speaker.                                                         |
| drop-in              | String  | Starts an immediate call with other speaker (by id).                                             |
| media-current-second | Number  | Current second for the media currently playing, allow seek.                                      |
| media-total-seconds  | Number  | Total seconds for the media currently playing.                                                   |
| media-progress       | Dimmer  | Played percentage for the media currently playing, allow seek.                                   |
| web-audio            | String  | Start playing a song by url using the browser player.                                            |
| web-video            | String  | Start playing a video by url using the browser player.                                           |
| youtube-id           | String  | Start playing a YouTube video (by id) (works for playlists adding the prefix 'playlist:').       |
| youtube-search       | String  | Start playing a YouTube video.                                                                   |
| spotify-id           | String  | Start playing in Spotify (by spotify uri).                                                       |
| spotify-search       | String  | Start playing a Spotify song.                                                                    |

## Thing Discovery

All the connected speakers can be automatically discovered using the main ui.

## Basic Usage:

* Remember to have installed and setup the default <b>speech-to-text, text-to-speech and interpreter</b> services using the main ui.
* As most browsers requires user interaction to access its audio capabilities, you should click at the speaker panel at first. It will then ask for user permissions and show a loading animation until it's ready.
* When the speaker is ready the registered sink and source will be visible in openHAB (audio settings section of the Main UI or though the console commands 'audio sinks' and 'audio sources').
* The speaker icon circle displays an animation whenever it's waiting for you to talk.
* The speaker icon dots part displays an animation while it's playing audio.
* By clicking on the speaker icon a single shot audio dialog processing will start. It uses the registered audio components (sink/source) and the default processing services configured on the openHAB voice settings.
* At this point you can discover the speaker using the main ui to add it as a thing in openHAB.

## Client keyword spotting

### Rustpotter

To run keyword spotting on the browser using rustpotter:

* Select 'Ruspotter Web' as ks in the thing configuration 'Voice' section.
* Configure the speaker keyword on the thing configuration 'Rustpotter Web' section.
* The rustpotter model should be available under '$OPENHAB_USERDATA/habspeaker/ks/rustpotter' or '$OPENHAB_USERDATA/rustpotter'.

For the keyword 'Hey openhab' the model should be named 'hey_openhab.rpw'.

## Media Providers

The idea behind this is take advance of the browser media capabilities and the official frameworks from legit projects or companies to display media on the ui allowing it's state to be controlled from the thing channels or the configured speaker voice commands.

Sources for each media providers can be locally configured in a json file containing an object where the keys are the human name of the source and the value the source id (url, youtube id, spotify uri...).

There are currently 4 media providers:

### WebVideo 

Uses a web video element to play the provided url.

You can send a url using the associated channel.

The voice search will look into the sources described in the '$OPENHAB_USERDATA/habspeaker/media/web-audio.json'.

### WebAudio

Uses a web audio element to play the configured url.

You can send a url using the associated channel.

The voice search will look into the sources described in the '$OPENHAB_USERDATA/habspeaker/media/web-video.json'.

### YouTube

No YouTube code is loaded until you play a video. Uses the official YouTube iframe api.

The voice search will look into the sources described in the '$OPENHAB_USERDATA/habspeaker/media/youtube.json' or fallback to the YouTube search.

For the YouTube search functionalities to work you need to have configured your api key for a Google Cloud project.
You can check the first step on the 'Calling the API' section [here](https://developers.google.com/youtube/v3/docs#calling-the-api), then enable YouTube Data API with this link (with your project id) 'https://console.developers.google.com/apis/api/youtube.googleapis.com/overview?project=$PROJECT_ID_HERE'.

If the search returns a channel, its 'uploaded videos playlist' will be loaded on the iframe.

Be aware that the YouTube data api have quotas. You can use the search around 100 times per day.

### Spotify

Requires a premium subscription.

No Spotify code is loaded if you don't configure your spotify app client id. It make use of the official spotify web api.

These are the required configuration steps:

* You need to create an app in the spotify developers dashboard [here](https://developer.spotify.com/dashboard/login), when created add this allowed redirect url "$YOUR_OPENHAB_URL/rest/habspeaker/spotify/login/callback".
* Then add the client id in the habspeaker general configuration **Settings / Other Services - HAB Speaker**.
* Now navigate to "$YOUR_OPENHAB_URL/rest/habspeaker/spotify/login/callback" and you should be redirected to the spotify login.
* If the login goes ok you will see a confirmation text on the top left of the page.

After this setup any speaker you start will be exposed as a remote player to spotify using its configured label. (opened speakers need to be restarted)

You can also control the player using the related voice commands, thing channels and the basic widget displayed on the HABSpeaker UI while playing.

The voice search will look into the sources described in the '$OPENHAB_USERDATA/habspeaker/media/youtube.json' or fallback to the Spotify search.

Note: spotify search just search by individual songs now, pending to improve.

## Electron App

The UI can be built as an electron application.

The electron application avoid two limitations of the web version:

* Do not requires https, it can be used over http.
* Do not requires an initial user interaction to use audio.

### Setup

The app expects a settings json file in the path '$HOME/.HABSpeaker/settings.json' with a content like:

```json
{"speakerId": "myspeakerid", "ohUrl": "http://192.168.1.200:8080", "ohToken": "oh.TokenNme.RANDOM"}

```

The 'ohToken' should be a persistent [openHAB API token](https://www.openhab.org/docs/configuration/apitokens.html). This token is not required if the OpenHAB 'Api Security' has 'Implicit User Role' enabled and the HABSpeaker 'Secure' setting has been turned off. 

### Spotify Support

The Spotify Web Sdk don't work on electron, so support is enabled thanks to the open source rust client [Librespot](https://github.com/librespot-org/librespot) (Requires a paid Spotify subscription).

Librespot credentials are persisted on '$HOME/.Librespot'.

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

The audio sink registered supports the following audio format: WAV PCM-SIGNED 16000hz 16-bit mono (single channel).
It's supported by most if not all the speech-to-text services available at openHAB. 

The audio source registered supports any wav or mp3 audio as it will try to convert it to the appropriate format (PCM-SIGNED 16-bit mono (or stereo) at 16000HZ).
