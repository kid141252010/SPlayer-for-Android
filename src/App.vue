<template>
  <div :class="['app-shell', `app-shell--${shellMode}`]">
    <Provider>
      <router-view />
    </Provider>
  </div>
</template>

<script setup lang="ts">
import { useDevice } from "@/composables/useDevice";
import { useImmersive } from "@/composables/useImmersive";

const { shellMode } = useDevice();

useImmersive();
</script>

<style scoped>
.app-shell {
  width: 100%;
  height: 100%;
}
</style>
