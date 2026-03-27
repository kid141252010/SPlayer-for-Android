<template>
  <div id="app-layout">
    <!-- 背景图 -->
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
    <!-- 主框架 -->
    <n-layout
      id="main"
      :class="{
        'pad-layout': isPad,
        'phone-layout': isPhone,
        'show-player': musicStore.isHasPlayer && statusStore.showPlayBar,
        'show-full-player': statusStore.showFullPlayer,
      }"
      :has-sider="isPad"
    >
      <!-- 侧边栏 -->
      <n-layout-sider
        v-if="isPad"
        id="main-sider"
        :style="{
          height:
            musicStore.isHasPlayer && statusStore.showPlayBar ? 'calc(100vh - 80px)' : '100vh',
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
        <!-- 导航栏 -->
        <Nav id="main-header" />
        <n-layout
          ref="contentRef"
          id="main-content"
          :native-scrollbar="false"
          :style="{
            '--layout-height': contentHeight,
          }"
          :content-style="{
            display: 'grid',
            gridTemplateRows: '1fr',
            minHeight: '100%',
            padding: isPhone ? '0 16px' : '0 24px',
          }"
          position="absolute"
          embedded
        >
          <!-- 路由页面 -->
          <RouterView v-slot="{ Component }">
            <Transition :name="`router-${settingStore.routeAnimation}`" mode="out-in">
              <KeepAlive v-if="settingStore.useKeepAlive" :max="20" :exclude="['layout']">
                <component :is="Component" class="router-view" />
              </KeepAlive>
              <component v-else :is="Component" class="router-view" />
            </Transition>
          </RouterView>
          <!-- 回顶 -->
          <n-back-top :right="40" :bottom="120">
            <SvgIcon :size="22" name="Up" />
          </n-back-top>
        </n-layout>
      </n-layout>
    </n-layout>
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
          <SvgIcon :name="item.icon" :size="22" />
          <span>{{ item.label }}</span>
        </button>
      </nav>
    </Transition>
    <!-- 播放列表 -->
    <SongPlayList />
    <!-- 全局播放器 -->
    <MainPlayer />
    <!-- 全屏播放器 -->
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

// 主内容
const contentRef = ref<HTMLElement | null>(null);

// 主内容高度
const { height: contentHeight } = useElementSize(contentRef);

// 加载背景图
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

// 初始化
useInit();

onMounted(() => {
  loadBackgroundImage();
  if (!isElectron) {
    window.addEventListener("beforeunload", (event) => {
      event.preventDefault();
      // 释放所有 blob URL
      blobURLManager.revokeAllBlobURLs();
      event.returnValue = "";
    });
  }
});
</script>

<style lang="scss" scoped>
#app-layout {
  --safe-area-top: max(env(safe-area-inset-top), 0px);
  --safe-area-bottom: max(env(safe-area-inset-bottom), 0px);
  --app-header-height: calc(72px + var(--safe-area-top));
  --phone-nav-height: 76px;
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
    .router-view {
      position: relative;
      height: 100%;
      &.n-result {
        display: flex;
        flex-direction: column;
        justify-content: center;
      }
    }
  }
  &.show-player {
    #main-content {
      bottom: 80px;
    }
  }
  &.phone-layout {
    #main-content {
      bottom: calc(var(--phone-nav-height) + var(--safe-area-bottom));
    }
    &.show-player {
      #main-content {
        bottom: calc(80px + var(--phone-nav-height) + var(--safe-area-bottom));
      }
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

.mobile-bottom-nav {
  position: fixed;
  left: 16px;
  right: 16px;
  bottom: calc(12px + var(--safe-area-bottom));
  z-index: 9;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  padding: 10px;
  border-radius: 24px;
  transition:
    transform 0.3s var(--n-bezier),
    bottom 0.3s var(--n-bezier),
    opacity 0.3s var(--n-bezier);

  &__item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 6px;
    min-height: 56px;
    border: 0;
    border-radius: 18px;
    background: transparent;
    color: var(--n-text-color-2);
    transition:
      background-color 0.3s var(--n-bezier),
      color 0.3s var(--n-bezier),
      transform 0.3s var(--n-bezier);

    span {
      font-size: 12px;
      line-height: 1;
    }

    &.active {
      color: var(--primary-hex);
      background: rgba(255, 255, 255, 0.32);
    }

    &:active {
      transform: scale(0.98);
    }
  }
}

.mobile-bottom-nav.is-raised {
  bottom: calc(92px + var(--safe-area-bottom));
}
</style>
