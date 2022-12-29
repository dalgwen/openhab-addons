import { app, BrowserWindow, shell } from 'electron';
import { release } from 'node:os';
import { join } from 'node:path';
import { registerAPIHandlers } from './native-api';
process.env.DIST = join(__dirname, './habspeaker');
process.env.LIBRESPOT_FOLDER = join(__dirname, 'librespot');

// Disable GPU Acceleration for Windows 7
if (release().startsWith('6.1')) app.disableHardwareAcceleration()

// Set application name for Windows 10+ notifications
if (process.platform === 'win32') app.setAppUserModelId(app.getName())

if (!app.requestSingleInstanceLock()) {
  app.quit();
  process.exit(0)
}

process.env['ELECTRON_DISABLE_SECURITY_WARNINGS'] = 'true'

let win: BrowserWindow | null = null
const preload = join(__dirname, 'preload.js');
const url = process.env.VITE_DEV_SERVER_URL;
const indexHtml = join(process.env.DIST, 'index.html');

async function createWindow() {
  win = new BrowserWindow({
    title: 'HAB Speaker',
    icon: join(process.env.DIST, 'favicon.svg'),
    webPreferences: {
      preload,
      nodeIntegration: false,
      contextIsolation: true,
    },
  });
  if (process.env.VITE_DEV_SERVER_URL) { // electron-vite-vue#298
    win.loadURL(url)
    win.webContents.openDevTools();
  } else {
    win.loadFile(indexHtml);
    win.webContents.openDevTools();
  }
  // Make all links open with the browser, not with the application
  win.webContents.setWindowOpenHandler(({ url }) => {
    // TODO: allow open openHAB login
    if (url.startsWith('https:')) shell.openExternal(url)
    return { action: 'deny' }
  })
}
function registerHABSpeakerHandlers() {
  return registerAPIHandlers(() => win);
}
// register api handlers and launch
app.whenReady().then(registerHABSpeakerHandlers).then(createWindow);

app.on('window-all-closed', () => {
  win = null
  if (process.platform !== 'darwin') app.quit()
})

app.on('second-instance', () => {
  if (win) {
    // Focus on the main window if the user tried to open another
    if (win.isMinimized()) win.restore()
    win.focus()
  }
})

app.on('activate', () => {
  const allWindows = BrowserWindow.getAllWindows()
  if (allWindows.length) {
    allWindows[0].focus()
  } else {
    createWindow()
  }
});