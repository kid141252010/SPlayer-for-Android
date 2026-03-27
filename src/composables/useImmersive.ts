import { onBeforeUnmount, onMounted } from "vue";
import { Capacitor } from "@capacitor/core";
import { StatusBar, Style } from "@capacitor/status-bar";

/**
 * Android immersive mode:
 * - overlays the webview under the status bar
 * - hides the status bar after the app boots
 */
export const useImmersive = () => {
  let reapplyTimer: number | undefined;

  const applyImmersive = async () => {
    if (Capacitor.getPlatform() !== "android") return;

    document.documentElement.classList.add("android-capacitor");

    try {
      await StatusBar.setStyle({ style: Style.Light });
      await StatusBar.setOverlaysWebView({ overlay: true });
      await StatusBar.hide();
    } catch (error) {
      console.warn("[useImmersive] Failed to enter immersive mode", error);
    }
  };

  const scheduleImmersive = (delay = 180) => {
    if (Capacitor.getPlatform() !== "android") return;

    window.clearTimeout(reapplyTimer);
    reapplyTimer = window.setTimeout(() => {
      void applyImmersive();
    }, delay);
  };

  const handleFocus = () => scheduleImmersive(120);
  const handleResize = () => scheduleImmersive(220);
  const handleOrientationChange = () => scheduleImmersive(260);
  const handleVisibilityChange = () => {
    if (document.visibilityState === "visible") {
      scheduleImmersive();
    }
  };

  onMounted(() => {
    void applyImmersive();
    window.addEventListener("focus", handleFocus);
    window.addEventListener("resize", handleResize);
    window.addEventListener("orientationchange", handleOrientationChange);
    document.addEventListener("visibilitychange", handleVisibilityChange);
  });

  onBeforeUnmount(() => {
    window.clearTimeout(reapplyTimer);
    window.removeEventListener("focus", handleFocus);
    window.removeEventListener("resize", handleResize);
    window.removeEventListener("orientationchange", handleOrientationChange);
    document.removeEventListener("visibilitychange", handleVisibilityChange);
  });

  return {
    applyImmersive,
  };
};
