import { computed } from "vue";
import { useWindowSize } from "@vueuse/core";

export const ANDROID_PAD_BREAKPOINT = 768;

/**
 * Android device layout strategy:
 * - >= 768px: pad layout
 * - < 768px: phone layout
 */
export const useDevice = () => {
  const { width, height } = useWindowSize();

  const shortestSide = computed(() => Math.min(width.value, height.value));
  const isLandscape = computed(() => width.value > height.value);
  const isPad = computed(() => shortestSide.value >= ANDROID_PAD_BREAKPOINT);
  const isPhone = computed(() => shortestSide.value < ANDROID_PAD_BREAKPOINT);
  const shellMode = computed(() => (isPad.value ? "pad" : "phone"));

  return {
    width,
    height,
    shortestSide,
    isLandscape,
    isPad,
    isPhone,
    shellMode,
    breakpoint: ANDROID_PAD_BREAKPOINT,
  };
};
