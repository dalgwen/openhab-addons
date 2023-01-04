<script setup>
import ErrorText from "../components/ErrorText.vue";
</script>

<template>
  <main>
    <ErrorText :message="'You are not allowed to connect to this server check your credentials.'" />
  </main>
</template>
