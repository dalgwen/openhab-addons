import { contextBridge, ipcRenderer } from 'electron';
let windowOnReadyCallback: (() => void) | undefined;
let loaded = false;
// expose electron to app
contextBridge.exposeInMainWorld('electronAPI', {
  onReady: (cb: () => void) => {
    if (!loaded) {
      windowOnReadyCallback = cb
    } else {
      cb();
    }
  },
  setLocalSettings: (localSettings: unknown) => ipcRenderer.invoke('setting:commit', localSettings),
  getSpeakerId: () => ipcRenderer.invoke('setting:speaker-id'),
  getTokenOpenHAB: () => ipcRenderer.invoke('setting:oh-token'),
  getUrlOpenHAB: () => ipcRenderer.invoke('setting:oh-url'),
  blockSystemSleep: (value: boolean) => ipcRenderer.invoke('sleep:block', value),
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
  const svgContent = `
  <svg width="100" height="100" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle id="big-circle" cx="50" cy="50" r="49" stroke="black" />
        <text x="8" y="56" class="title-hab">
          HAB
        </text>
        <text x="40" y="56" class="title-speaker">
          Speaker
        </text>
      </svg>
  `;
  const styleContent = `
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
  #logo {
    width: 300px;
    height: 300px;
    font-family: sans-serif;
    display: flex;
    justify-content: center;
    align-items: center;
  }
  #logo svg {
    width: 100%;
    height: 100%;
  }
  #big-circle {
    stroke: transparent;
    stroke-width: 2px;
    fill: transparent;
    stroke-dasharray: 310.51519775390625;
    stroke-dashoffset: 310.51519775390625;
    animation: circle-animation 3s linear forwards 3s, fill 3s ease forwards 5s;
  }
  .title-hab {
    stroke: #474747;
    stroke-width: 0.5px;
    stroke-linecap: butt;
    stroke-linejoin: miter;
    fill: #e64a19;
  }
  .title-speaker {
    fill: #474747;
  }
  svg path {
    fill: transparent;
    stroke: #474747;
    stroke-dasharray: 379.0550231933594;
    stroke-dashoffset: 379.0550231933594;
    animation: letter-anim 3s linear forwards;
    animation-delay: 4s;
  }
  @keyframes circle-animation {
    0% {
      stroke-dashoffset: 310.51519775390625;
    }
  
    30% {
      /* multiple the initial value of the property*/
      stroke-dashoffset: 621.0303955078124;
      stroke: #474747;
    }
  
    70% {
      /* initial value*4 */
      stroke-dashoffset: 1243.060791015625;
      stroke: #e64a19;
    }
  
    100% {
      stroke-dashoffset: 1243.860791015625;
      stroke: #e64a19;
    }
  }
  
  @keyframes letter-anim {
    to {
      stroke: #474747;
      stroke-dashoffset: 0;
      fill: #474747;
      stroke-width: 0px;
    }
  }
  @keyframes fill {
    from {
      fill: transparent;
    }
    to {
      fill: #ee4e1e;
    }
  }  
    `
  const oStyle = document.createElement('style')
  const oDiv = document.createElement('div')

  oStyle.id = 'app-loading-style'
  oStyle.innerHTML = styleContent
  oDiv.innerHTML = `<div class="app-loading-wrap"><div id="logo">${svgContent}</div></div>`;

  return {
    appendLoading() {
      safeDOM.append(document.head, oStyle)
      safeDOM.append(document.body, oDiv)
    },
    removeLoading() {
      safeDOM.remove(document.head, oStyle)
      safeDOM.remove(document.body, oDiv)
    }
  }
}

// ----------------------------------------------------------------------

const { appendLoading, removeLoading } = useLoading()
domReady().then(appendLoading)
setTimeout(() => {
  loaded = true;
  if (windowOnReadyCallback) {
    windowOnReadyCallback();
    windowOnReadyCallback = null;
  }
  removeLoading();
}, 5999);