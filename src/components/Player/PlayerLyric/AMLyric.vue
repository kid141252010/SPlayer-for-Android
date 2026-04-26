<template>
  <Transition name="fade" mode="out-in">
    <div
      :key="amLyricsData?.[0]?.words?.length"
      :class="[
        'lyric-am',
        {
          pure: statusStore.pureLyricMode,
          duet: hasDuet,
          'align-right': settingStore.lyricAlignRight,
        },
      ]"
      :style="{
        '--amll-lp-color': 'rgb(var(--main-cover-color, 239 239 239))',
        '--amll-lp-hover-bg-color': statusStore.playerMetaShow
          ? 'rgba(var(--main-cover-color), 0.08)'
          : 'transparent',
        '--amll-lyric-left-padding': settingStore.lyricAlignRight
          ? ''
          : `${settingStore.lyricHorizontalOffset}px`,
        '--amll-lyric-right-padding': settingStore.lyricAlignRight
          ? `${settingStore.lyricHorizontalOffset}px`
          : '',
      }"
    >
      <div v-if="statusStore.lyricLoading" class="lyric-loading">歌词正在加载中...</div>
      <LyricPlayer
        v-else
        ref="lyricPlayerRef"
        :lyricLines="amLyricsData"
        :currentTime="amllDisplayTime"
        :playing="statusStore.playStatus"
        :enableSpring="settingStore.useAMSpring"
        :enableScale="settingStore.useAMSpring"
        :alignPosition="effectiveLyricsScrollOffset"
        :alignAnchor="effectiveLyricsScrollOffset > 0.4 ? 'center' : 'top'"
        :enableBlur="settingStore.lyricsBlur"
        :hidePassedLines="settingStore.hidePassedLines"
        :wordFadeWidth="settingStore.wordFadeWidth"
        :style="{
          '--display-count-down-show': settingStore.countDownShow ? 'flex' : 'none',
          '--amll-lp-font-size': getFontSize(
            settingStore.lyricFontSize,
            settingStore.lyricFontSizeMode,
          ),
          'font-weight': settingStore.lyricFontWeight,
          'font-family': settingStore.LyricFont !== 'follow' ? settingStore.LyricFont : '',
          ...lyricLangFontStyle(settingStore),
        }"
        class="am-lyric"
        @line-click="jumpSeek"
        @line-tap="jumpSeekByLine"
      />
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { LyricLineMouseEvent, type LyricLine } from "@applemusic-like-lyrics/core";
import { useMusicStore, useSettingStore, useStatusStore } from "@/stores";
import { getLyricLanguage } from "@/utils/format";
import { usePlayerController } from "@/core/player/PlayerController";
import { cloneDeep } from "lodash-es";
import { isCapacitorAndroid } from "@/utils/env";
import { lyricLangFontStyle } from "@/utils/lyric/lyricFontConfig";
import { getFontSize } from "@/utils/style";

const props = defineProps({
  currentTime: {
    type: Number,
    default: 0,
  },
});

const musicStore = useMusicStore();
const statusStore = useStatusStore();
const settingStore = useSettingStore();
const player = usePlayerController();

const lyricPlayerRef = ref<any | null>(null);
type AmlLyricLineEvent = {
  lineIndex: number;
  line: {
    getLine: () => LyricLine;
  };
};

const effectiveLyricsScrollOffset = computed(() =>
  isCapacitorAndroid
    ? Math.max(0, settingStore.lyricsScrollOffset - 0.08)
    : settingStore.lyricsScrollOffset,
);

// 当前歌词
const amLyricsData = computed(() => {
  const { songLyric } = musicStore;
  if (!songLyric) return [];
  // 优先使用逐字歌词(YRC/TTML)
  const useYrc = songLyric.yrcData?.length && settingStore.showWordLyrics;
  const lyrics = useYrc ? songLyric.yrcData : songLyric.lrcData;
  // 简单检查歌词有效性
  if (!Array.isArray(lyrics) || lyrics.length === 0) return [];
  // 此处cloneDeep 删除会暴毙 不要动
  const clonedLyrics = cloneDeep(lyrics) as LyricLine[];
  // 处理歌词内容
  const { showTran, showRoma, showWordsRoma, swapTranRoma, lyricAlignRight } = settingStore;
  clonedLyrics.forEach((line) => {
    // 处理显隐
    if (!showTran) line.translatedLyric = "";
    if (!showRoma) line.romanLyric = "";
    if (!showWordsRoma) line.words?.forEach((word) => (word.romanWord = ""));
    // 调换翻译与音译位置
    if (swapTranRoma) {
      const temp = line.translatedLyric;
      line.translatedLyric = line.romanLyric;
      line.romanLyric = temp;
    }
    // 处理对唱方向反转
    if (lyricAlignRight) {
      line.isDuet = !line.isDuet;
    }
  });
  return clonedLyrics;
});

// 是否有对唱行
const hasDuet = computed(() => amLyricsData.value?.some((line) => line.isDuet) ?? false);

const isValidLyricTime = (time: unknown): time is number =>
  typeof time === "number" && Number.isFinite(time) && time >= 0;

// 获取原始歌词行的真实发声时间
const getLineSeekTime = (line?: LyricLine) => {
  const firstWordStartTime = line?.words?.find(
    (word) => word.word?.trim() && isValidLyricTime(word.startTime),
  )?.startTime;

  if (isValidLyricTime(firstWordStartTime)) return firstWordStartTime;
  if (isValidLyricTime(line?.startTime)) return line.startTime;
  return undefined;
};

const amllSeekCompensation = ref<{
  advanceMs: number;
  lineStartTime: number;
  lineEndTime: number;
} | null>(null);

const amllDisplayTime = computed(() => {
  const compensation = amllSeekCompensation.value;
  if (!compensation) return props.currentTime;

  if (
    !Number.isFinite(compensation.advanceMs) ||
    !Number.isFinite(compensation.lineStartTime) ||
    (!Number.isFinite(compensation.lineEndTime) && compensation.lineEndTime !== Infinity) ||
    compensation.advanceMs <= 0 ||
    props.currentTime < compensation.lineStartTime ||
    props.currentTime >= compensation.lineEndTime
  ) {
    return props.currentTime;
  }

  return props.currentTime - compensation.advanceMs;
});

const getOriginalLyricTime = (lineIndex: number) => getLineSeekTime(amLyricsData.value[lineIndex]);
const getOriginalLyricEndTime = (lineIndex: number) => {
  const line = amLyricsData.value[lineIndex];
  if (!Number.isFinite(line?.startTime)) return undefined;
  if (Number.isFinite(line?.endTime) && Number(line?.endTime) > Number(line?.startTime)) {
    return line?.endTime;
  }
  const nextLine = amLyricsData.value[lineIndex + 1];
  if (
    Number.isFinite(nextLine?.startTime) &&
    Number(nextLine?.startTime) > Number(line?.startTime)
  ) {
    return nextLine?.startTime;
  }
  // 末行无结束时间时，补偿持续到歌曲结束
  return Infinity;
};

const seekToOriginalPlaybackTime = (time: number | undefined) => {
  if (typeof time !== "number" || !Number.isFinite(time)) return;
  // 播放跳转只使用原始歌词时间，不叠加歌词偏移或 AMLL 提前时间
  player.setSeek(time);
  player.play();
};

const seekToOriginalLyricLine = (lineIndex: number) => {
  seekToOriginalPlaybackTime(getOriginalLyricTime(lineIndex));
};

const startAmlSeekCompensation = (line: AmlLyricLineEvent) => {
  const originalTime = getOriginalLyricTime(line.lineIndex);
  const originalEndTime = getOriginalLyricEndTime(line.lineIndex);
  // AMLL 内部时间只用于触摸后的显示补偿，不能作为播放跳转目标
  const amllTime = line.line.getLine()?.startTime;
  if (
    !Number.isFinite(originalTime) ||
    (!Number.isFinite(originalEndTime) && originalEndTime !== Infinity) ||
    !Number.isFinite(amllTime)
  ) {
    amllSeekCompensation.value = null;
    return;
  }

  const advanceMs = Number(originalTime) - Number(amllTime);
  amllSeekCompensation.value =
    advanceMs > 0
      ? {
          advanceMs,
          lineStartTime: Number(originalTime),
          lineEndTime: Number(originalEndTime),
        }
      : null;
};

// 进度跳转
const jumpSeek = (line: LyricLineMouseEvent) => {
  seekToOriginalLyricLine(line.lineIndex);
};

const jumpSeekByLine = (line: AmlLyricLineEvent) => {
  if (!isCapacitorAndroid) return;
  startAmlSeekCompensation(line);
  seekToOriginalLyricLine(line.lineIndex);
};

// 处理歌词语言
const processLyricLanguage = (player = lyricPlayerRef.value) => {
  const lyricLineObjects = player?.lyricPlayer?.currentLyricLineObjects;
  if (!Array.isArray(lyricLineObjects) || lyricLineObjects.length === 0) {
    return;
  }
  // 遍历歌词行
  for (let e of lyricLineObjects) {
    // 获取歌词行内容 (合并逐字歌词为一句)
    const content = e.lyricLine.words.map((word: any) => word.word).join("");
    // 跳过空行
    if (!content) continue;
    // 获取歌词语言
    const lang = getLyricLanguage(content);
    // 为主歌词设置 lang 属性 (firstChild 获取主歌词 不为翻译和音译设置属性)
    e.element.firstChild.setAttribute("lang", lang);
  }
};

// 切换歌曲时处理歌词语言
watch(amLyricsData, (data) => {
  amllSeekCompensation.value = null;
  if (data) nextTick(() => processLyricLanguage());
});
watch(lyricPlayerRef, (player) => {
  if (player) nextTick(() => processLyricLanguage(player));
});
</script>

<style lang="scss" scoped>
.lyric-am {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  isolation: isolate;

  :deep(.am-lyric) {
    width: 100%;
    height: 100%;
    position: absolute;
    left: 0;
    top: 0;
    padding-left: var(--amll-lyric-left-padding, 10px);
    padding-right: 80px;
    div {
      div[class^="_interludeDots"] {
        display: var(--display-count-down-show);
      }
    }
    @media (max-width: 990px) {
      padding: 0;
      margin-left: 0;
      .amll-lyric-player {
        > div {
          padding-left: 20px;
          padding-right: 20px;
        }
      }
    }
  }

  &.align-right {
    :deep(.am-lyric) {
      padding-left: 80px;
      padding-right: var(--amll-lyric-right-padding, 10px);

      @media (max-width: 990px) {
        padding: 0;
        margin-right: -20px;
      }
      @media (max-width: 500px) {
        margin-right: 0;
      }
    }
  }
  &.pure {
    &:not(.duet) {
      text-align: center;

      :deep(.am-lyric) div {
        transform-origin: center;
      }
    }

    :deep(.am-lyric) {
      margin: 0;
      padding: 0 80px;
    }
  }

  :deep(.am-lyric div[class*="lyricMainLine"] span) {
    text-align: start;
  }

  :lang(ja) {
    font-family: var(--ja-font-family);
  }
  :lang(en) {
    font-family: var(--en-font-family);
  }
  :lang(ko) {
    font-family: var(--ko-font-family);
  }
}

.lyric-loading {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--amll-lp-color, #efefef);
  font-size: 22px;
}
</style>
