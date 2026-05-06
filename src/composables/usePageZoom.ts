import { onMounted, watch } from "vue";
import { useSettingStore } from "@/stores";

/**
 * 页面缩放
 *
 * 通过动态修改 viewport meta 的 initial-scale 实现：
 * - WebView 会按新 scale 重新计算视口，window.innerWidth / vw / media query 都会正确反映
 * - 比 CSS zoom 更可靠，不会出现 vw 单位不跟随导致的内容裁切或留白
 */
export const usePageZoom = () => {
  const settingStore = useSettingStore();

  const notifyResize = () => {
    // 视口变化后主动触发 resize，让 VueUse / Naive UI 等库重算布局
    const fire = () => window.dispatchEvent(new Event("resize"));
    fire();
    requestAnimationFrame(fire);
    setTimeout(fire, 150);
  };

  const apply = (zoom: number) => {
    const safe = Math.max(50, Math.min(200, Number(zoom) || 100));
    const ratio = safe / 100;

    // 动态更新 viewport meta，替代 CSS zoom
    let viewport = document.querySelector('meta[name="viewport"]') as HTMLMetaElement | null;
    if (!viewport) {
      viewport = document.createElement("meta");
      viewport.name = "viewport";
      document.head.appendChild(viewport);
    }
    viewport.setAttribute(
      "content",
      `width=device-width, initial-scale=${ratio}, viewport-fit=cover`,
    );

    // 清除之前可能残留的 CSS zoom，避免双重缩放
    (document.documentElement.style as CSSStyleDeclaration & { zoom?: string }).zoom = "";
    document.documentElement.style.removeProperty("--zoom-ratio");

    notifyResize();
  };

  onMounted(() => apply(settingStore.pageZoom));
  watch(
    () => settingStore.pageZoom,
    (val) => apply(val),
  );
};
