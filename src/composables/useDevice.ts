import { computed } from "vue";
import { useWindowSize } from "@vueuse/core";

export const ANDROID_PAD_BREAKPOINT = 768;

export const useDevice = () => {
  const { width, height } = useWindowSize();

  const shortestSide = computed(() => Math.min(width.value, height.value));
  const isLandscape = computed(() => width.value > height.value);
  const isPad = computed(() => shortestSide.value >= ANDROID_PAD_BREAKPOINT);
  const isPhone = computed(() => shortestSide.value < ANDROID_PAD_BREAKPOINT);
  const isPhonePortrait = computed(() => isPhone.value && !isLandscape.value);
  const isPhoneLandscape = computed(() => isPhone.value && isLandscape.value);
  const shellMode = computed(() => (isPad.value ? "pad" : "phone"));

  return {
    width,
    height,
    shortestSide,
    isLandscape,
    isPad,
    isPhone,
    isPhonePortrait,
    isPhoneLandscape,
    shellMode,
    breakpoint: ANDROID_PAD_BREAKPOINT,
  };
};
