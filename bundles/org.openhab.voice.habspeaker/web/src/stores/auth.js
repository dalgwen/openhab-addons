import { defineStore } from "pinia";
import axios from "axios";
import { useAssistantStore } from "./assistant";
import { ohAuthHelper } from "../utils/openhab-auth-helper";
export const useAuthStore = defineStore("auth", () => {
  const { renewToken } = useAssistantStore();
  function getAccessToken() {
    return ohAuthHelper.getAccessToken();
  }
  async function isTokenRequired() {
    let requireToken = true;
    // determine whether the token is required for user operations
    await axios
      .get("/rest/habspeaker/config")
      .then((resp) => {
        requireToken = resp.data.secure;
      })
      .catch((err) => {
        if (err === "Unauthorized" || err === 401) requireToken = true;
        return Promise.resolve();
      });
    return requireToken;
  }
  function unauthorized() {
    const mainUrl = document.location.origin;
    console.debug("Unauthorized, redirecting to main url: " + mainUrl);
    if (!import.meta.env.DEV) {
      ohAuthHelper.authorize();
    } else {
      console.warn("Redirection disabled in dev mode");
    }
  }
  async function refreshAccessToken() {
    await ohAuthHelper.tryExchangeAuthorizationCode();
    await ohAuthHelper.refreshAccessToken((err, data) => {
      if(err) {
        return unauthorized();
      }
      renewToken(data.access_token);
    });
  }
  return { isTokenRequired, getAccessToken, refreshAccessToken };
});
