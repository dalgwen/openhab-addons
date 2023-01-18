<script setup lang="ts">
import { platform } from "../platforms";
import { ref } from "vue";
import router from "../router";
import { useSettingsStore } from "../stores/settings";
import axios, { AxiosError } from "axios";
const { setSpeakerSettings, getSpeakerId, getOHToken, getOHUrl } = useSettingsStore();
const model = ref({
  speakerId: "",
  ohUrl: "",
  ohToken: "",
});
const errorMessage = ref("");
let displayToken = ref(false);
let displayURL = ref(false);
platform.getName().then(async name => {
  model.value.speakerId = await getSpeakerId() ?? '';
  model.value.ohToken = await getOHToken() ?? '';
  model.value.ohUrl = await getOHUrl() ?? '';
  switch (name) {
    case "web":
      displayToken.value = false;
      displayURL.value = false;
      break;
    case "capacitor":
      displayToken.value = true;
      displayURL.value = true;
      break;
    case "electron":
      displayToken.value = true;
      displayURL.value = true;
      break;
  }
});
async function start() {
  var { speakerId, ohToken, ohUrl } = model.value;
  // validate
  if (!speakerId.length) {
    errorMessage.value = "Speaker id is required";
    return;
  }
  if (displayURL.value) {
    if (!ohUrl.length) {
      errorMessage.value = "OpenHAB server url is required";
      return;
    }
    if (!/^https?:\/\/(?:www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b(?:[-a-zA-Z0-9()@:%_\+.~#?&\/=]*)$/.test(ohUrl)) {
      errorMessage.value = "Invalid url";
      return;
    }
  }
  if (displayURL.value || displayToken.value) {
    const connection_state = await checkServerConnection(speakerId, ohUrl, ohToken);
    if (connection_state == 'bad') {
      // validate server connection
      errorMessage.value = "Unable to ping OpenHAB server, review your url";
      return;
    }
    if (displayToken.value && connection_state == 'require_auth') {
      // validate server connection
      errorMessage.value = "OpenHAB server authorization failed, token is invalid";
      return;
    }
  }
  errorMessage.value = "";

  // store
  await setSpeakerSettings({ speakerId, ohToken, ohUrl });
  if (await getSpeakerId()) {
    // start speaker
    await router.replace('/');
  }
}
async function checkServerConnection(speakerId: string, ohUrl: string, ohToken: string): Promise<'ok' | 'require_auth' | 'bad'> {
  if(import.meta.env.VITE_DEV_SERVER_URL) {
    // in dev mode swap to server dev url
    ohUrl = import.meta.env.VITE_DEV_SERVER_URL;
  }
  const headers = {
    accept: "application/json",
  } as { [key: string]: string };
  if (ohToken?.length) { headers["Authorization"] = "Bearer " + ohToken; }
  try {
    (await axios.get<any>(`${ohUrl}/rest/habspeaker/config/${speakerId}`)).data;
    return 'ok';
  } catch (error) {
    if (error instanceof AxiosError && error.response?.status === 401) {
      return 'require_auth';
    }
    return 'bad';
  }
}
</script>
<template>
  <div class="container">
    <div class="form">
      <div class="form-group">
        <label for="id">Speaker Id</label>
        <input type="text" id="id" class="form-control" v-model="model.speakerId"
          placeholder="Identifier against openHAB" onkeydown="return /[0-9a-zA-Z\-\_]/i.test(event.key)" />
      </div>
      <div v-if="displayURL" class="form-group">
        <label for="id">OpenHAB URL:</label>
        <input type="text" id="ohUrl" class="form-control" v-model="model.ohUrl" placeholder="Your personal openHAB URL"
          onkeydown="return " />
      </div>
      <div v-if="displayToken" class="form-group">
        <label for="id">OpenHAB API Token:</label>
        <input type="text" id="ohToken" class="form-control" v-model="model.ohToken"
          placeholder="A persistent api token" />
      </div>
      <span class="error-msg" v-if="errorMessage.length">{{ errorMessage }}</span>
      <div class="form-buttons">
        <button @click="start()">Start Speaker</button>
      </div>
    </div>
  </div>
</template>

<style lang="css" scoped>
.container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 86vh;
  background-color: var(--color-internal-background);
}

.container label {
  margin: 0 8px;
  font-size: 14px;
  color: var(--color-form-label);
}

.container .error-msg {
  margin: 0 8px;
  color: rgba(199, 10, 10, 0.5);
}

.container .form {
  position: absolute;
  padding: 30px 20px;
  width: 320px;
  border-radius: 7px;
  background-color: white;
  backdrop-filter: blur(5px);
  background-color: rgba(158, 189, 199, 0.288);
  overflow: hidden;
}

.container input {
  padding: 8px 10px;
  margin: 3px 8px 16px 8px;
  background-color: rgba(222, 239, 248, 0.877);
  border: 0px transparent;
  border-radius: 5px;
  font-size: 16px;
  hyphens: auto;
  z-index: 1;
}

.form-group {
  display: grid;
}

.form-buttons {
  text-align: right;
}

.form-buttons button {
  cursor: pointer;
  padding: 8px 14px;
  margin: 20px 8px 0 0;
  border-width: 0px;
  border-radius: 5px;
  color: white;
  background-color: #b69269;
}
</style>
