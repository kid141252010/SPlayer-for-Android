import { onMounted, onUnmounted } from "vue";
import { App as CapacitorApp, type PluginListenerHandle } from "@capacitor/app";
import { Capacitor } from "@capacitor/core";
import { useRouter } from "vue-router";

const ROOT_PATHS = new Set(["/", "/home"]);

export const useAndroidBack = () => {
  const router = useRouter();
  let listener: PluginListenerHandle | null = null;

  onMounted(async () => {
    if (Capacitor.getPlatform() !== "android") return;

    listener = await CapacitorApp.addListener("backButton", async () => {
      const currentPath = router.currentRoute.value.path;
      const hasBackHistory = window.history.length > 1 && !ROOT_PATHS.has(currentPath);

      if (hasBackHistory) {
        await router.back();
        return;
      }

      await CapacitorApp.exitApp();
    });
  });

  onUnmounted(() => {
    listener?.remove();
    listener = null;
  });
};
