import type { CoverType, SongType } from "@/types/main";
import { useStatusStore } from "@/stores";
import { useDevice } from "@/composables/useDevice";

export const useListDetail = () => {
  const statusStore = useStatusStore();
  const { isPhone } = useDevice();

  const detailData = ref<CoverType | null>(null);
  const listData = shallowRef<SongType[]>([]);
  const loading = ref<boolean>(true);

  const getSongListHeight = (listScrolling: boolean) => {
    if (isPhone.value) {
      const phoneHeaderHeight = listScrolling ? 72 : 132;
      return Math.max(statusStore.mainContentHeight - phoneHeaderHeight, 320);
    }

    const normalHeight = 240;
    const smallHeight = 120;
    return statusStore.mainContentHeight - (listScrolling ? smallHeight : normalHeight);
  };

  const resetData = (resetList: boolean = true) => {
    detailData.value = null;
    if (resetList) {
      listData.value = [];
    }
  };

  const setDetailData = (data: CoverType | null) => {
    detailData.value = data;
  };

  const setListData = (data: SongType[]) => {
    listData.value = data;
  };

  const appendListData = (data: SongType[]) => {
    listData.value = [...listData.value, ...data];
  };

  const setLoading = (value: boolean) => {
    loading.value = value;
  };

  return {
    detailData,
    listData,
    loading,
    getSongListHeight,
    resetData,
    setDetailData,
    setListData,
    appendListData,
    setLoading,
  };
};
