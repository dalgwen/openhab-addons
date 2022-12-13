import { fileURLToPath, URL } from "node:url";

import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { VitePWA } from "vite-plugin-pwa";

// https://vitejs.dev/config/
export default defineConfig({
  base: "/habspeaker/",
  plugins: [
    vue(),
    VitePWA({
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
    })
  ],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
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
});
