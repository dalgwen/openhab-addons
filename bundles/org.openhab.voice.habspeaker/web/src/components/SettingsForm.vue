<script setup>
import { storeToRefs } from "pinia";
import { ref } from "vue";
import { useAssistantStore } from "../stores/assistant";
import { useSettingsStore } from "../stores/settings";
const store = useSettingsStore();
const assistantStore = useAssistantStore();
const { speakerId } = storeToRefs(store);
const model = ref({
  id: speakerId.value,
});
function reset() {
  model.value.id = speakerId.value;
}
function save() {
  speakerId.value = model.value.id;
  store.commit();
  assistantStore.resetConnection(
    speakerId.value,
  );
}
</script>
<template>
  <div class="container">
    <div class="form">
      <div class="form-group">
        <label for="id">Speaker Id</label>
        <input
          type="text"
          id="id"
          class="form-control"
          v-model="model.id"
          onkeydown="return /[0-9a-zA-Z\-\_]/i.test(event.key)"
        />
      </div>
      <div class="form-buttons">
        <button @click="save()">Save</button>
        <button @click="reset()">Reset</button>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 86vh;
  background-color: var(--color-internal-background);
}

.container label {
  color: var(--color-form-label);
}

.form-group {
  display: grid;
  margin-bottom: 1rem;
}

.form-buttons {
  text-align: right;
}

.form-buttons button {
  margin: 0.2rem;
}
</style>
