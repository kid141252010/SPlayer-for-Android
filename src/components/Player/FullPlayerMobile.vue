<template>
  <div ref="mobileStart" class="full-player-mobile">
    <div class="top-bar">
      <div class="btn" @click.stop="statusStore.showFullPlayer = false">
        <SvgIcon name="Down" :size="26" />
      </div>
    </div>

    <div
      :class="['mobile-content', { swiping: isPageSwiping }]"
      :style="{ transform: contentTransform }"
      @click.stop
    >
      <div class="page info-page">
        <div class="cover-section">
          <PlayerCover :no-lyric="true" />
        </div>

        <div class="info-group">
          <div class="song-info-bar">
            <div class="info-section">
              <PlayerData :center="false" :light="false" class="mobile-data" />
            </div>
            <div class="info-actions">
              <div
                v-if="musicStore.playSong.type !== 'radio'"
                class="action-btn"
                @click="
                  toLikeSong(musicStore.playSong, !dataStore.isLikeSong(musicStore.playSong.id))
                "
              >
                <SvgIcon
                  :name="
                    dataStore.isLikeSong(musicStore.playSong.id) ? 'Favorite' : 'FavoriteBorder'
                  "
                  :size="26"
                  :class="{ liked: dataStore.isLikeSong(musicStore.playSong.id) }"
                />
              </div>
              <div
                class="action-btn"
                @click.stop="openPlaylistAdd([musicStore.playSong], !!musicStore.playSong.path)"
              >
                <SvgIcon name="AddList" :size="26" />
              </div>
            </div>
          </div>

          <div class="progress-section">
            <span class="time" @click="toggleTimeFormat">{{ timeDisplay[0] }}</span>
            <PlayerSlider class="player" :show-tooltip="false" />
            <span class="time" @click="toggleTimeFormat">{{ timeDisplay[1] }}</span>
          </div>

          <div class="control-section">
            <template v-if="musicStore.playSong.type !== 'radio' && !statusStore.personalFmMode">
              <div class="mode-btn" @click.stop="player.toggleShuffle()">
                <SvgIcon
                  :name="statusStore.shuffleIcon"
                  :size="24"
                  :depth="statusStore.shuffleMode === 'off' ? 3 : 1"
                />
              </div>
            </template>
            <div v-else class="placeholder"></div>

            <div class="ctrl-btn" @click.stop="player.nextOrPrev('prev')">
              <SvgIcon name="SkipPrev" :size="36" />
            </div>

            <n-button
              :loading="statusStore.playLoading"
              class="play-btn"
              type="primary"
              strong
              secondary
              circle
              @click.stop="player.playOrPause()"
            >
              <template #icon>
                <Transition name="fade" mode="out-in">
                  <SvgIcon
                    :key="statusStore.playStatus ? 'Pause' : 'Play'"
                    :name="statusStore.playStatus ? 'Pause' : 'Play'"
                    :size="40"
                  />
                </Transition>
              </template>
            </n-button>

            <div class="ctrl-btn" @click.stop="player.nextOrPrev('next')">
              <SvgIcon name="SkipNext" :size="36" />
            </div>

            <template v-if="musicStore.playSong.type !== 'radio' && !statusStore.personalFmMode">
              <div class="mode-btn" @click.stop="player.toggleRepeat()">
                <SvgIcon
                  :name="statusStore.repeatIcon"
                  :size="24"
                  :depth="statusStore.repeatMode === 'off' ? 3 : 1"
                />
              </div>
            </template>
            <div v-else class="placeholder"></div>
          </div>
        </div>
      </div>

      <div class="page lyric-page">
        <div class="lyric-header">
          <s-image :src="musicStore.getSongCover('s')" class="lyric-cover" />
          <div class="lyric-info">
            <div class="name text-hidden">
              {{
                settingStore.hideBracketedContent
                  ? removeBrackets(musicStore.playSong.name)
                  : musicStore.playSong.name
              }}
            </div>
            <div class="artist text-hidden">{{ artistName }}</div>
          </div>
          <div
            v-if="musicStore.playSong.type !== 'radio'"
            class="action-btn"
            @click.stop="
              toLikeSong(musicStore.playSong, !dataStore.isLikeSong(musicStore.playSong.id))
            "
          >
            <SvgIcon
              :name="dataStore.isLikeSong(musicStore.playSong.id) ? 'Favorite' : 'FavoriteBorder'"
              :size="24"
              :class="{ liked: dataStore.isLikeSong(musicStore.playSong.id) }"
            />
          </div>
        </div>
        <div class="lyric-main">
          <PlayerLyric />
        </div>
      </div>
    </div>

    <div v-if="hasLyric" class="pagination">
      <div
        v-for="i in 2"
        :key="i"
        :class="['dot', { active: pageIndex === i - 1 }]"
        @click="pageIndex = i - 1"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useEventListener, useSwipe } from "@vueuse/core";
import { useMusicStore, useStatusStore, useDataStore, useSettingStore } from "@/stores";
import { usePlayerController } from "@/core/player/PlayerController";
import { useTimeFormat } from "@/composables/useTimeFormat";
import { toLikeSong } from "@/utils/auth";
import { openPlaylistAdd } from "@/utils/modal";
import { removeBrackets } from "@/utils/format";

const musicStore = useMusicStore();
const statusStore = useStatusStore();
const settingStore = useSettingStore();
const dataStore = useDataStore();
const player = usePlayerController();
const { timeDisplay, toggleTimeFormat } = useTimeFormat();

const mobileStart = ref<HTMLElement | null>(null);
const pageIndex = ref(0);
const ignorePageSwipe = ref(false);

const hasLyric = computed(() => musicStore.isHasLrc && musicStore.playSong.type !== "radio");

const artistName = computed(() => {
  const artists = musicStore.playSong.artists;
  if (Array.isArray(artists)) {
    return artists.map((artist) => artist.name).join(" / ");
  }
  return (artists as string) || "未知艺术家";
});

watch(hasLyric, (value) => {
  if (!value) pageIndex.value = 0;
});

const isLyricTouchTarget = (target: EventTarget | null) =>
  target instanceof Element && !!target.closest(".lyric-main");

const { direction, isSwiping, lengthX } = useSwipe(mobileStart, {
  threshold: 10,
  onSwipeStart: (event) => {
    ignorePageSwipe.value = isLyricTouchTarget(event.target);
  },
  onSwipeEnd: () => {
    if (ignorePageSwipe.value) return;
    if (!hasLyric.value) return;

    if (direction.value === "left" && lengthX.value > 100) {
      pageIndex.value = 1;
    } else if (direction.value === "right" && lengthX.value < -100) {
      pageIndex.value = 0;
    }
  },
});

useEventListener(
  window,
  ["touchend", "touchcancel"],
  () => {
    ignorePageSwipe.value = false;
  },
  { passive: true },
);

const isPageSwiping = computed(() => isSwiping.value && !ignorePageSwipe.value);

const contentTransform = computed(() => {
  const baseOffset = pageIndex.value * 50;
  if (!isPageSwiping.value || !hasLyric.value) {
    return `translateX(-${baseOffset}%)`;
  }

  let pixelOffset = lengthX.value;
  if (pageIndex.value === 0 && pixelOffset < 0) {
    pixelOffset *= 0.3;
  }
  if (pageIndex.value === 1 && pixelOffset > 0) {
    pixelOffset *= 0.3;
  }

  return `translateX(calc(-${baseOffset}% - ${pixelOffset}px))`;
});
</script>

<style lang="scss" scoped>
.full-player-mobile {
  --mobile-safe-top: max(env(safe-area-inset-top), 0px);
  --mobile-safe-bottom: max(env(safe-area-inset-bottom), 0px);
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;

  .top-bar {
    position: absolute;
    inset: 0 0 auto;
    height: calc(56px + var(--mobile-safe-top));
    display: flex;
    align-items: center;
    justify-content: flex-end;
    padding: var(--mobile-safe-top) 20px 0;
    z-index: 10;

    .btn {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: background-color 0.2s;

      &:active {
        background-color: rgba(255, 255, 255, 0.1);
      }

      .n-icon {
        color: rgb(var(--main-cover-color));
        opacity: 0.8;
      }
    }
  }

  .mobile-content {
    flex: 1;
    display: flex;
    width: 200%;
    height: 100%;
    transition: transform 0.3s cubic-bezier(0.25, 1, 0.5, 1);

    &.swiping {
      transition: none;
    }

    .page {
      width: 50%;
      height: 100%;
      flex-shrink: 0;
      position: relative;
    }
  }

  .info-page {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 0 20px calc(24px + var(--mobile-safe-bottom));
    overflow-y: auto;

    .cover-section {
      width: 100%;
      min-height: clamp(220px, 42vh, 420px);
      margin-top: calc(52px + var(--mobile-safe-top));
      margin-bottom: 16px;
      display: flex;
      align-items: center;
      justify-content: center;

      :deep(.player-cover) {
        width: min(100%, clamp(240px, 72vw, 380px));

        &.record {
          width: clamp(220px, 64vw, 360px);

          .cover-img {
            width: clamp(220px, 64vw, 360px);
            height: clamp(220px, 64vw, 360px);
            min-width: clamp(220px, 64vw, 360px);
          }

          .pointer {
            width: clamp(56px, 16vw, 88px);
            top: clamp(-72px, -12vw, -52px);
          }
        }
      }
    }

    .info-group {
      width: 100%;
      display: flex;
      flex-direction: column;
    }

    .song-info-bar {
      width: 100%;
      display: flex;
      justify-content: space-between;
      margin-bottom: 20px;

      .info-section {
        flex: 1;
        min-width: 0;
        margin-right: 12px;

        :deep(.mobile-data) {
          width: 100%;
          max-width: 100%;

          .name {
            margin-left: 0;
          }
        }
      }

      .info-actions {
        display: flex;
        gap: 16px;
        padding-top: 20px;
        flex-shrink: 0;
      }
    }

    .action-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      border-radius: 50%;
      cursor: pointer;
      transition: background-color 0.2s;

      &:active {
        background-color: rgba(255, 255, 255, 0.1);
      }

      .n-icon {
        color: rgb(var(--main-cover-color));
        opacity: 0.6;
        transition:
          opacity 0.2s,
          transform 0.2s;

        &.liked {
          fill: rgb(var(--main-cover-color));
          opacity: 1;
        }
      }
    }

    .progress-section {
      display: flex;
      align-items: center;
      margin: 0 0 24px;

      .time {
        width: 40px;
        font-size: 12px;
        text-align: center;
        color: rgb(var(--main-cover-color));
        opacity: 0.6;
        font-variant-numeric: tabular-nums;
      }

      .n-slider {
        margin: 0 12px;
      }
    }

    .control-section {
      width: 100%;
      max-width: 420px;
      margin: 0 auto 24px;
      padding: 0;
      display: flex;
      align-items: center;
      justify-content: space-between;

      .placeholder {
        width: 24px;
      }

      .mode-btn {
        width: 40px;
        height: 40px;
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        opacity: 0.8;

        .n-icon {
          color: rgb(var(--main-cover-color));
        }
      }

      .ctrl-btn {
        width: 50px;
        height: 50px;
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;

        .n-icon {
          color: rgb(var(--main-cover-color));
        }
      }

      .play-btn {
        width: 60px;
        height: 60px;
        font-size: 26px;
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        transition: transform 0.2s;
        background-color: rgba(var(--main-cover-color), 0.2);
        color: rgb(var(--main-cover-color));

        &.n-button--primary-type {
          --n-color: rgba(var(--main-cover-color), 0.14);
          --n-color-hover: rgba(var(--main-cover-color), 0.2);
          --n-color-focus: rgba(var(--main-cover-color), 0.2);
          --n-color-pressed: rgba(var(--main-cover-color), 0.12);
        }

        &:active {
          transform: scale(0.95);
        }
      }
    }
  }

  .lyric-page {
    padding: calc(56px + var(--mobile-safe-top)) 20px calc(24px + var(--mobile-safe-bottom));
    display: flex;
    flex-direction: column;

    .lyric-header {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-bottom: 20px;
      flex-shrink: 0;
      padding-top: 8px;

      .lyric-cover {
        width: 50px;
        height: 50px;
        flex-shrink: 0;
        border-radius: 6px;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);

        :deep(img) {
          width: 100%;
          height: 100%;
          border-radius: 6px;
        }
      }

      .lyric-info {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        justify-content: center;

        .name {
          font-size: 18px;
          font-weight: bold;
          margin-bottom: 2px;
        }

        .artist {
          font-size: 13px;
          opacity: 0.6;
        }
      }
    }

    .lyric-main {
      flex: 1;
      min-height: 0;
      position: relative;
    }
  }

  .pagination {
    position: absolute;
    left: 0;
    right: 0;
    bottom: calc(16px + var(--mobile-safe-bottom));
    display: flex;
    justify-content: center;
    gap: 8px;
    pointer-events: none;

    .dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background-color: rgba(255, 255, 255, 0.2);
      transition: all 0.3s;
      pointer-events: auto;

      &.active {
        width: 16px;
        border-radius: 4px;
        background-color: rgb(var(--main-cover-color));
        opacity: 0.8;
      }
    }
  }

  @media (max-width: 512px) {
    .top-bar {
      padding: var(--mobile-safe-top) 16px 0;
    }

    .info-page {
      padding: 0 16px calc(20px + var(--mobile-safe-bottom));

      .cover-section {
        min-height: clamp(200px, 38vh, 320px);
        margin-top: calc(48px + var(--mobile-safe-top));
      }

      .song-info-bar {
        margin-bottom: 16px;
      }

      .control-section {
        .ctrl-btn {
          width: 44px;
          height: 44px;
        }
      }
    }

    .lyric-page {
      padding: calc(52px + var(--mobile-safe-top)) 16px calc(20px + var(--mobile-safe-bottom));

      .lyric-header {
        gap: 12px;
      }
    }
  }
}
</style>
