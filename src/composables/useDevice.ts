import { computed, ref, watchEffect } from "vue";
import { useSettingStore } from "@/stores";

export const ANDROID_PAD_BREAKPOINT = 768;

export const useDevice = () => {
  const settingStore = useSettingStore();

  const rawWidth = ref(window.innerWidth);
  const rawHeight = ref(window.innerHeight);

  const refreshSize = () => {
    rawWidth.value = window.innerWidth;
    rawHeight.value = window.innerHeight;
  };

  // viewport 缩放变化时，innerWidth 会变，需要主动刷新
  watchEffect(() => {
    const _ = settingStore.pageZoom;
    refreshSize();
    requestAnimationFrame(refreshSize);
    setTimeout(refreshSize, 200);
  });

  if (typeof window !== "undefined") {
    window.addEventListener("resize", refreshSize);
    window.addEventListener("orientationchange", () => setTimeout(refreshSize, 300));
  }

  // 等效视口（viewport meta 的 initial-scale 已把缩放反映在 innerWidth 里，无需再除）
  const effectiveWidth = computed(() => rawWidth.value);
  const effectiveHeight = computed(() => rawHeight.value);

  const shortestSide = computed(() => Math.min(effectiveWidth.value, effectiveHeight.value));
  const isLandscape = computed(() => effectiveWidth.value > effectiveHeight.value);
  const isPad = computed(() => shortestSide.value >= ANDROID_PAD_BREAKPOINT);
  const isPhone = computed(() => shortestSide.value < ANDROID_PAD_BREAKPOINT);
  const isPhonePortrait = computed(() => isPhone.value && !isLandscape.value);
  const isPhoneLandscape = computed(() => isPhone.value && isLandscape.value);
  const shellMode = computed(() => (isPad.value ? "pad" : "phone"));

  return {
    width: rawWidth,
    height: rawHeight,
    // 用于布局判断的等效像素
    effectiveWidth,
    effectiveHeight,
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
