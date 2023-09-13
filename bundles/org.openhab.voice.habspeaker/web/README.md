# Description

Web UI for HABSpeaker audio ws.

# Third Party Icons

Microphone: https://icon-icons.com/es/icono/microfono-audio/61507 (from Maria & Guillem) (License: https://creativecommons.org/licenses/by/4.0/)

Speaker: https://icon-icons.com/es/download/111240/SVG/512/ (from Aaron Jin) (License: Free)

# Developer Hints

## Project structure

The project is written in typescript and bundles with Vite,
does not rely on any ui framework because the visual part of the project is simple.

These are the relevant files and folders:

- The `index.html` file contains the html for all ui the elements.
- Used svg images are in the `src/icons`folder.
- The `src/styles` folder contains the ui css files separated by functionality but exported in a common index.
- The `src/io` module encapsulates the WebSocket connection to the server handles the connection between the audio transmitted and the Web Audio API, so it's the central piece of the project and exposes a `IOMain` class that allows to initialize it and listen certain events to propagate them to the UI.
- The `src/ui-controls` module contains the ui components logic divided by functionality: tooltip, screen-saver, widget, options-form and media.
- The `src/platform` module encapsulates some platform specific code, only the code for the target platform is bundled.
Current platforms are: browser, electron and capacitor.
- The `src/api.ts` file contains the HTTP calls to the api.
Also handles the OAuth flow against the openHAB authorization page using the code in `oh-oauth.ts` (ported from MainUI). 
- The `src/main.ts` file is the bundle entry, handles the authorization if needed, ensure local options are in place, initializes platform, the ui controllers and the io, and setup the connection between them.

## IO Overview

As commented most of the complexity of this project is inside this module.

It makes use of the WebAudioAPI (Worklet support is required), a Worker and a WebSocket connection to stream audio from your device to your openHAB server.

The WebSocket connection to your server is stablish inside the Worker and also the audio resampling (sample rate and format conversion) happens there, so it do not overload the main thread, once initialized the worker will try to keep this connection open and notify the main thread about its state.
Sample rate conversion can be skipped and done in the server if configured.

The worker instruct the main thread about whetter it should start/stop the audio streaming.
The when notified the main thread sets up an AudioWorklet instances to transfer the audio to/from the WebAudioAPI to the worker, by transferring the MessagePort of the Worklet instance to the worker thread, and connect the worker to the AudioContext source or destination.

The io only implements sending one outgoing audio stream, because the audio source data is piped to the different consumers in the openHAB server.

The io implements receiving multiple concurrent incoming audio streams as it uses a basic protocol implementation between the client and the addon that allows the client to identify the stream of the audio chucks sent over the WebSocket and whether an audio transmission has ended and the resources needed to play that stream can be disconnect from the audio context and disposed.

`The reason to include a resampler library is because Safari does not play well when changing the AudioContext sample rate, so in order to allow low-bandwidth audio this is included. There is an advanced option to change the AudioContext sample rate among the speaker thing configuration. Note that the project does not contains browser specific code.`

## Project Setup

```sh
npm install
```

### Compile and Hot-Reload for Development with OH proxy

```sh
OH_PROXY=http://192.168.1.100:8080 npm run dev
```

### Compile and Minify for Production

```sh
npm run build
```

### Compile for development (Source maps and without Minify)

```sh
npm run build:dev
```

### Lint with [ESLint](https://eslint.org/)

```sh
npm run lint
```
