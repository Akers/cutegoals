import { ref, shallowRef } from 'vue';

/**
 * 页面级数据加载：统一 loading / 错误 / 重试语义（对齐旧版 React useApi 的页面状态）。
 *
 * - 失败时的错误提示由 http 层全局弹出（见 src/utils/http/alova/index.ts 的 responded 钩子），
 *   页面捕获后切换到错误态（全屏错误结果 + 重试按钮）。
 * - 静默请求（meta.quiet，如初始化）不弹全局提示。
 */
export function usePageData<T>(fetcher: () => Promise<T>) {
  const data = shallowRef<T | null>(null);
  const loading = ref(false);
  const errorMsg = ref('');

  async function reload() {
    loading.value = true;
    errorMsg.value = '';
    try {
      data.value = await fetcher();
    } catch (e) {
      errorMsg.value = (e as Error)?.message || '未知错误';
    } finally {
      loading.value = false;
    }
  }

  void reload();

  return { data, loading, errorMsg, reload };
}
