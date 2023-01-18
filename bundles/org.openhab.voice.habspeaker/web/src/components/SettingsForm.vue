<script setup lang="ts">
import { storeToRefs } from "pinia";
import { ref } from "vue";
import { useIOStore } from "../stores/io";
import { useSettingsStore } from "../stores/settings";
const { getSpeakerId, setSpeakerId } = useSettingsStore();
const ioStore = useIOStore();
const model = ref({
  id: "",
});
reset();
async function reset() {
  model.value.id = await getSpeakerId();
}
function save() {
  var id = model.value.id;
  setSpeakerId(id);
  ioStore.resetConnection(id);
}
</script>
<template>
  <div class="container">
    <div class="form">
      <div class="form-group">
        <label for="id">Speaker Id</label>
        <input type="text" id="id" class="form-control" v-model="model.id"
          onkeydown="return /[0-9a-zA-Z\-\_]/i.test(event.key)" />
      </div>
      <div class="form-buttons">
        <button @click="save()">Save</button>
        <button @click="reset()">Reset</button>
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
