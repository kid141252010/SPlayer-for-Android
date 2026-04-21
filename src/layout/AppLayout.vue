<template>
  <div id="app-layout">
    <Transition name="fade">
      <div
        v-if="
          (statusStore.themeBackgroundMode === 'image' ||
            statusStore.themeBackgroundMode === 'video') &&
          statusStore.backgroundImageUrl
        "
        :key="statusStore.backgroundImageUrl"
        class="background-container"
      >
        <div
          v-if="statusStore.themeBackgroundMode === 'image'"
          class="background-image"
          :style="{
            backgroundImage: `url(${statusStore.backgroundImageUrl})`,
            transform: `scale(${statusStore.backgroundConfig.scale})`,
            filter: `blur(${statusStore.backgroundConfig.blur}px)`,
          }"
        />
        <video
          v-else-if="statusStore.themeBackgroundMode === 'video'"
          class="background-image"
          :src="statusStore.backgroundImageUrl"
          autoplay
          loop
          muted
          :style="{
            objectFit: 'cover',
            transform: `scale(${statusStore.backgroundConfig.scale})`,
            filter: `blur(${statusStore.backgroundConfig.blur}px)`,
          }"
        />
        <div
          class="background-mask"
          :style="{
            backgroundColor: `rgba(0, 0, 0, ${statusStore.backgroundConfig.maskOpacity / 100})`,
          }"
        />
      </div>
    </Transition>

    <div
      id="main"
      :class="{
        'pad-layout': isPad,
        'phone-layout': isPhone,
        'show-player': musicStore.isHasPlayer && statusStore.showPlayBar,
        'show-full-player': statusStore.showFullPlayer,
      }"
    >
      <n-layout v-if="isPad" id="pad-main" has-sider>
        <n-layout-sider
          id="main-sider"
          :style="{
            height:
              musicStore.isHasPlayer && statusStore.showPlayBar ? 'calc(100dvh - 80px)' : '100dvh',
          }"
          :content-style="{
            overflow: 'hidden',
            height: '100%',
            padding: '0',
          }"
          :native-scrollbar="false"
          :collapsed="statusStore.menuCollapsed"
          :collapsed-width="64"
          :width="240"
          collapse-mode="width"
          show-trigger="bar"
          bordered
          @collapse="statusStore.menuCollapsed = true"
          @expand="statusStore.menuCollapsed = false"
        >
          <Sider />
        </n-layout-sider>

        <n-layout id="main-layout">
          <Nav id="main-header" />
          <n-layout
            ref="contentRef"
            id="main-content"
            :native-scrollbar="false"
            :style="{ '--layout-height': contentHeight }"
            :content-style="{
              display: 'grid',
              gridTemplateRows: '1fr',
              minHeight: '100%',
              boxSizing: 'border-box',
              padding: '0 24px',
            }"
            position="absolute"
            embedded
          >
            <RouterView v-slot="{ Component }">
              <Transition :name="`router-${settingStore.routeAnimation}`" mode="out-in">
                <KeepAlive v-if="settingStore.useKeepAlive" :max="20" :exclude="['layout']">
                  <component :is="Component" class="router-view" />
                </KeepAlive>
                <component v-else :is="Component" class="router-view" />
              </Transition>
            </RouterView>
            <n-back-top :right="40" :bottom="120">
              <SvgIcon :size="22" name="Up" />
            </n-back-top>
          </n-layout>
        </n-layout>
      </n-layout>

      <div v-else id="main-phone-layout">
        <Nav id="main-header" />
        <main ref="contentRef" id="main-phone-content">
          <RouterView v-slot="{ Component }">
            <Transition :name="`router-${settingStore.routeAnimation}`" mode="out-in">
              <KeepAlive v-if="settingStore.useKeepAlive" :max="20" :exclude="['layout']">
                <component :is="Component" class="router-view" />
              </KeepAlive>
              <component v-else :is="Component" class="router-view" />
            </Transition>
          </RouterView>
          <n-back-top :right="16" :bottom="phoneBackTopBottom">
            <SvgIcon :size="22" name="Up" />
          </n-back-top>
        </main>
      </div>
    </div>

    <Transition name="fade">
      <nav
        v-if="isPhone && !statusStore.showFullPlayer"
        :class="[
          'mobile-bottom-nav',
          { 'is-raised': musicStore.isHasPlayer && statusStore.showPlayBar },
        ]"
      >
        <button
          v-for="item in phoneNavItems"
          :key="item.key"
          :class="['mobile-bottom-nav__item', { active: activePhoneNav === item.key }]"
          type="button"
          @click="navigatePhoneNav(item.routeName)"
        >
          <SvgIcon :name="item.icon" :size="17" />
          <span>{{ item.label }}</span>
        </button>
      </nav>
    </Transition>

    <SongPlayList />
    <MainPlayer />
    <PlayerProvider>
      <FullPlayer />
    </PlayerProvider>
  </div>
</template>

<script setup lang="ts">
import { useMusicStore, useStatusStore, useSettingStore, useDataStore } from "@/stores";
import { useBlobURLManager } from "@/core/resource/BlobURLManager";
import { isElectron } from "@/utils/env";
import { useDevice } from "@/composables/useDevice";
import { useInit } from "@/composables/useInit";

const musicStore = useMusicStore();
const statusStore = useStatusStore();
const settingStore = useSettingStore();
const dataStore = useDataStore();
const route = useRoute();
const router = useRouter();

const blobURLManager = useBlobURLManager();
const { isPad, isPhone } = useDevice();

const phoneNavItems = [
  { key: "home", label: "推荐", icon: "Home", routeName: "home" },
  { key: "discover", label: "发现", icon: "Discover", routeName: "discover" },
  { key: "like", label: "收藏", icon: "Star", routeName: "like" },
  { key: "history", label: "最近", icon: "History", routeName: "history" },
] as const;

const activePhoneNav = computed(() => {
  const routeName = String(route.name || "");

  if (routeName.startsWith("discover")) return "discover";
  if (routeName.startsWith("like")) return "like";
  if (routeName.startsWith("history")) return "history";
  if (routeName === "home") return "home";

  return "home";
});

const contentRef = ref<HTMLElement | null>(null);
const { height: contentHeight } = useElementSize(contentRef);

// 手机端"回到顶部"按钮的底部偏移，避开底部导航与播放条
const phoneBackTopBottom = computed(() => {
  const navHeight = 60; // mobile-bottom-nav 高度（含 padding）
  const playerHeight = 80; // 播放条高度
  const hasPlayer = musicStore.isHasPlayer && statusStore.showPlayBar;
  // 基础偏移：导航栏之上 + 间距
  const base = navHeight + 16;
  return hasPlayer ? base + playerHeight : base;
});

const loadBackgroundImage = async () => {
  if (statusStore.backgroundImageUrl) return;
  if (statusStore.themeBackgroundMode === "image" || statusStore.themeBackgroundMode === "video") {
    const blob = await dataStore.getBackgroundImage();
    if (blob) {
      const arrayBuffer = await blob.arrayBuffer();
      statusStore.backgroundImageUrl = blobURLManager.createBlobURL(
        arrayBuffer,
        blob.type,
        "background-image",
      );
    }
  }
};

watchEffect(() => {
  statusStore.mainContentHeight = contentHeight.value;
});

const navigatePhoneNav = (routeName: (typeof phoneNavItems)[number]["routeName"]) => {
  if (route.name === routeName) return;
  router.push({ name: routeName });
};

useInit();

// 横竖屏切换后强制刷新布局：触发 resize 事件帮助依赖视口尺寸的组件重新计算
const handleOrientationChange = () => {
  // 触发多次 resize 事件以覆盖不同时机：立即、动画中、完成后
  const fire = () => window.dispatchEvent(new Event("resize"));
  fire();
  requestAnimationFrame(fire);
  setTimeout(fire, 150);
  setTimeout(fire, 400);
};

onMounted(() => {
  loadBackgroundImage();
  window.addEventListener("orientationchange", handleOrientationChange);
  // matchMedia 在部分设备上比 orientationchange 事件更可靠
  const orientationMql = window.matchMedia("(orientation: portrait)");
  orientationMql.addEventListener?.("change", handleOrientationChange);
  if (!isElectron) {
    window.addEventListener("beforeunload", (event) => {
      event.preventDefault();
      blobURLManager.revokeAllBlobURLs();
      event.returnValue = "";
    });
  }
});

onBeforeUnmount(() => {
  window.removeEventListener("orientationchange", handleOrientationChange);
});
</script>

<style lang="scss" scoped>
#app-layout {
  --safe-area-top: max(env(safe-area-inset-top), 0px);
  --safe-area-bottom: max(env(safe-area-inset-bottom), 0px);
  --app-header-height: calc(72px + var(--safe-area-top));
  --phone-nav-height: 52px;
  --phone-content-gap: 12px;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
}

.background-container {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: -1;
  pointer-events: none;
  overflow: hidden;

  .background-image {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background-size: cover;
    background-position: center;
    background-repeat: no-repeat;
    transform-origin: center center;
  }

  .background-mask {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
  }
}

#main {
  flex: 1;
  height: 100%;
  transition:
    transform 0.3s var(--n-bezier),
    opacity 0.3s var(--n-bezier);

  .router-view {
    position: relative;
    min-height: 100%;

    &.n-result {
      display: flex;
      flex-direction: column;
      justify-content: center;
    }
  }

  &.show-full-player {
    opacity: 0;
    transform: scale(0.9);

    #main-header {
      -webkit-app-region: no-drag;
    }
  }
}

#pad-main {
  height: 100%;

  #main-layout {
    background: linear-gradient(
      180deg,
      rgba(var(--background), 0.86),
      rgba(var(--background), 0.78)
    );
  }

  #main-content {
    top: var(--app-header-height);
    background-color: transparent;
    transition: bottom 0.3s;
  }
}

#main.show-player {
  #pad-main {
    #main-content {
      bottom: 80px;
    }
  }
}

#main-phone-layout {
  display: flex;
  flex-direction: column;
  height: 100dvh;
  min-height: 100dvh;
  background: linear-gradient(180deg, rgba(var(--background), 0.94), rgba(var(--background), 0.9));
}

#main-phone-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0 16px calc(var(--phone-nav-height) + var(--safe-area-bottom) + 8px);
  box-sizing: border-box;
}

#main.show-player {
  #main-phone-content {
    padding-bottom: calc(80px + var(--phone-nav-height) + var(--safe-area-bottom) + 8px);
  }
}

.mobile-bottom-nav {
  position: fixed;
  left: 10px;
  right: 10px;
  bottom: calc(8px + var(--safe-area-bottom));
  z-index: 9;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px;
  padding: 4px;
  border-radius: 16px;
  backdrop-filter: blur(16px);
  background-color: rgba(var(--background), 0.8);
  box-shadow: 0 3px 14px rgba(0, 0, 0, 0.1);
  transition:
    transform 0.3s var(--n-bezier),
    bottom 0.3s var(--n-bezier),
    opacity 0.3s var(--n-bezier);

  &__item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    min-height: 34px;
    padding: 2px 0;
    border: 0;
    border-radius: 10px;
    background: transparent;
    color: var(--n-text-color-2);
    transition:
      background-color 0.3s var(--n-bezier),
      color 0.3s var(--n-bezier),
      transform 0.3s var(--n-bezier);

    .n-icon {
      font-size: 15px;
    }

    span {
      font-size: 8px;
      line-height: 1;
      text-align: center;
      word-break: keep-all;
    }

    &.active {
      color: var(--primary-hex);
      background: rgba(var(--primary), 0.12);
    }

    &:active {
      transform: scale(0.95);
    }
  }

  @media (max-width: 512px) {
    left: 8px;
    right: 8px;
    bottom: calc(8px + var(--safe-area-bottom));
    padding: 5px;
    gap: 3px;
    border-radius: 16px;

    &__item {
      min-height: 38px;
      gap: 2px;
      border-radius: 11px;

      span {
        font-size: 8px;
      }

      .n-icon {
        font-size: 14px;
      }
    }
  }
}

.mobile-bottom-nav.is-raised {
  bottom: calc(78px + var(--safe-area-bottom));

  @media (max-width: 512px) {
    bottom: calc(72px + var(--safe-area-bottom));
  }
}
</style>
