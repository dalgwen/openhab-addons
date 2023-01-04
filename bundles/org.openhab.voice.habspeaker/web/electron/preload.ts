const { contextBridge, ipcRenderer } = require('electron');
let windowOnReadyCallback: (() => void) | undefined;
// expose electron to app
contextBridge.exposeInMainWorld('electronAPI', {
  onReady: (cb: () => void) => windowOnReadyCallback = cb,
  getSpeakerId: () => ipcRenderer.invoke('setting:speaker-id'),
  getTokenOpenHAB: () => ipcRenderer.invoke('setting:oh-token'),
  getUrlOpenHAB: () => ipcRenderer.invoke('setting:oh-url'),
  isSpotifyAvailable: () => ipcRenderer.invoke('spotify:available'),
  startSpotify: (label: string) => ipcRenderer.invoke('spotify:start', label),
  stopSpotify: () => ipcRenderer.invoke('spotify:stop'),
  getSpotifyId: () => ipcRenderer.invoke('spotify:id'),
  setSpotifyToken: (spotifyToken: string) => ipcRenderer.invoke('spotify:token', spotifyToken),
  setSpotifyPlaybackListener: (listener: (state: string) => void) => ipcRenderer.on('spotify:status', (_, state: string) => listener(state)),
});


// Display loading
function domReady(condition: DocumentReadyState[] = ['complete', 'interactive']) {
  return new Promise((resolve) => {
    if (condition.includes(document.readyState)) {
      resolve(true)
    } else {
      document.addEventListener('readystatechange', () => {
        if (condition.includes(document.readyState)) {
          resolve(true)
        }
      })
    }
  })
}

const safeDOM = {
  append(parent: HTMLElement, child: HTMLElement) {
    if (!Array.from(parent.children).find(e => e === child)) {
      return parent.appendChild(child)
    }
  },
  remove(parent: HTMLElement, child: HTMLElement) {
    if (Array.from(parent.children).find(e => e === child)) {
      return parent.removeChild(child)
    }
  },
}

/**
 * https://tobiasahlin.com/spinkit
 * https://connoratherton.com/loaders
 * https://projects.lukehaas.me/css-loaders
 * https://matejkustec.github.io/SpinThatShit
 */
function useLoading() {
  const className = `loaders-css__square-spin`
  const styleContent = `
@keyframes square-spin {
  25% { transform: perspective(100px) rotateX(180deg) rotateY(0); }
  50% { transform: perspective(100px) rotateX(180deg) rotateY(180deg); }
  75% { transform: perspective(100px) rotateX(0) rotateY(180deg); }
  100% { transform: perspective(100px) rotateX(0) rotateY(0); }
}
.${className} > div {
  animation-fill-mode: both;
  width: 50px;
  height: 50px;
  background: #fff;
  animation: square-spin 3s 0s cubic-bezier(0.09, 0.57, 0.49, 0.9) infinite;
}
.app-loading-wrap {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #282c34;
  z-index: 9;
}
    `
  const oStyle = document.createElement('style')
  const oDiv = document.createElement('div')

  oStyle.id = 'app-loading-style'
  oStyle.innerHTML = styleContent
  oDiv.className = 'app-loading-wrap'
  oDiv.innerHTML = `<div class="${className}"><div></div></div>`

  return {
    appendLoading() {
      safeDOM.append(document.head, oStyle)
      safeDOM.append(document.body, oDiv)
    },
    removeLoading() {
      // TODO: check config file is ok
      if (true) {
        safeDOM.remove(document.head, oStyle)
        safeDOM.remove(document.body, oDiv)
      } else {
        oDiv.innerHTML = "<div><span>Missing HABSpeaker configuration.</span></div>"
      }
    }
  }
}

// ----------------------------------------------------------------------

const { appendLoading, removeLoading } = useLoading()
domReady().then(appendLoading)
setTimeout(() => {
  if(windowOnReadyCallback) {
    windowOnReadyCallback();
    windowOnReadyCallback = null;
  }
  removeLoading();
}, 4999)