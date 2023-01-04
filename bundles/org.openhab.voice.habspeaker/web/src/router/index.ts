import { createRouter, createWebHashHistory, createWebHistory } from "vue-router";
import Assistant from "../views/AssistantView.vue";

const router = createRouter({
  history: import.meta.env.MODE === 'electron' ? createWebHashHistory() : createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/",
      name: "speaker",
      component: Assistant,
    },
    {
      path: "/settings",
      name: "settings",
      component: () => import("../views/SettingsView.vue"),
    },
    {
      path: "/audio-error",
      name: "audio error",
      component: () => import("../views/AudioErrorView.vue"),
    },
    {
      path: "/error",
      name: "error",
      component: () => import("../views/ErrorView.vue"),
    },
    {
      path: "/unauthorized",
      name: "unauthorized",
      component: () => import("../views/UnauthorizedView.vue"),
    },
  ],
});

export default router;
