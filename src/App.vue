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
import { useAndroidBack } from "@/composables/useAndroidBack";

const { shellMode } = useDevice();

useImmersive();
useAndroidBack();
</script>

<style scoped>
.app-shell {
  width: 100%;
  height: 100%;
}
</style>
