import { fileURLToPath, URL } from "node:url";

import { defineConfig, PluginOption } from "vite";
import vue from "@vitejs/plugin-vue";
import { VitePWA } from "vite-plugin-pwa";
import ConditionalCompile from "vite-plugin-conditional-compiler";
const plugins: PluginOption[] = [ConditionalCompile(), vue()];
const isDevelopment = process.env.NODE_ENV === "development" || !!process.env.VSCODE_DEBUG
const isProduction = process.env.NODE_ENV === "production";
let baseUrl: string | undefined;
if (process.env.ELECTRON_BUILD) {
  console.log("Building HAB Speaker for electron");
  const { rmSync } = require('node:fs');
  rmSync('dist-electron', { recursive: true, force: true });
  plugins.push(getElectronPlugin());
} else {
  console.log("Building HAB Speaker PWA");
  baseUrl = "/habspeaker/";
  plugins.push(getPWAPlugin());
}
// https://vitejs.dev/config/
export default defineConfig({
  base: baseUrl,
  plugins,
  envDir: 'env',
  server: {
    proxy: {
      "/habspeaker/ws": {
        target: "ws://127.0.0.1:8080",
        ws: true,
      },
      "/rest": {
        target: "http://127.0.0.1:8080",
      },
    },
  },
  clearScreen: false,
});

// Platform Plugins
function getPWAPlugin() {
  return VitePWA({
    registerType: 'autoUpdate',
    manifest: {
      name: "HAB Speaker",
      short_name: "HABSpeaker",
      description: "Dialog processing over WebSocket for openHAB",
      display: "fullscreen",
      background_color: "#ff6600",
      theme_color: "#000000",
      icons: [
        {
          "src": "/habspeaker/icons/icon-72x72.png",
          "sizes": "72x72",
          "type": "image/png"
        },
        {
          "src": "/habspeaker/icons/icon-192x192.png",
          "sizes": "192x192",
          "type": "image/png"
        },
        {
          "src": "/habspeaker/icons/icon-256x256.png",
          "sizes": "256x256",
          "type": "image/png"
        },
        {
          "src": "/habspeaker/icons/icon-384x384.png",
          "sizes": "384x384",
          "type": "image/png"
        },
        {
          "src": "/habspeaker/icons/icon-512x512.png",
          "sizes": "512x512",
          "type": "image/png"
        }
      ]
    },
  });
}

function getElectronPlugin() {
  const electron = require('vite-plugin-electron').default;
  return electron([
    {
      // Main-Process entry file of the Electron App.
      entry: 'electron/index.ts',
      onstart(options) {
        if (process.env.VSCODE_DEBUG) {
          console.log(/* For `.vscode/.debug.script.mjs` */'[startup] Electron App')
        } else {
          options.startup()
        }
      },
      vite: {
        build: {
          sourcemap: isDevelopment,
          minify: isProduction,
          outDir: 'dist-electron/',
          // rollupOptions: {
          //   external: Object.keys("dependencies" in pkg ? pkg.dependencies : {}),
          // },
        },
      },
    },
    {
      entry: 'electron/preload.ts',
      onstart(options) {
        // Notify the Renderer-Process to reload the page when the Preload-Scripts build is complete, 
        // instead of restarting the entire Electron App.
        options.reload()
      },
      vite: {
        build: {
          sourcemap: isDevelopment,
          minify: isProduction,
          outDir: 'dist-electron/',
          // rollupOptions: {
          //   external: Object.keys("dependencies" in pkg ? pkg.dependencies : {}),
          // },
        },
      },
    }
  ]);
}