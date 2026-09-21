import { createAlova } from 'alova';
import VueHook from 'alova/vue';
import adapterFetch from 'alova/fetch';
import { createAlovaMockAdapter } from '@alova/mock';
import { isString } from 'lodash-es';
import mocks from './mocks';
import { useUser } from '@/store/modules/user';
import { useGlobSetting, useLocalSetting } from '@/hooks/setting';
import { PageEnum } from '@/enums/pageEnum';
import { CSRF_HEADER_NAME, getCsrfToken } from '@/utils/http/csrf';
import { getErrorMessage } from '@/utils/http/errorCodes';
import { isUrl } from '@/utils';

const { apiUrl, urlPrefix } = useGlobSetting();

const { useMock, loggerMock } = useLocalSetting();

/** 后端统一信封的成功标识（server/common/.../dto/ApiResponse.java） */
const SUCCESS_CODE = 'SUCCESS';

const mockAdapter = createAlovaMockAdapter([...mocks], {
  // 全局控制是否启用mock接口，默认为true
  enable: useMock,

  // 非模拟请求适配器，用于未匹配mock接口时发送请求
  httpAdapter: adapterFetch(),

  // mock接口响应延迟，单位毫秒
  delay: 1000,

  // 自定义打印mock接口请求信息
  // mockRequestLogger: (res) => {
  //   loggerMock && console.log(`Mock Request ${res.url}`, res);
  // },
  mockRequestLogger: loggerMock,
  onMockError(error, currentMethod) {
    console.error('🚀 ~ onMockError ~ currentMethod:', currentMethod);
    console.error('🚀 ~ onMockError ~ error:', error);
  },
});

/**
 * 会话失效处理：清理本地用户状态，并根据当前路径前缀静默跳转到对应区域登录页
 * （与旧版 React 控制台 AuthContext.handleUnauthorized 行为一致：/admin/* → /admin/login，
 * 其余 → /parent/login；登录/初始化页本身不跳转）。
 */
function handleUnauthorized() {
  const userStore = useUser();
  userStore.reset();

  const { pathname } = window.location;
  const publicPaths: string[] = [PageEnum.PARENT_LOGIN, PageEnum.ADMIN_LOGIN, PageEnum.ADMIN_INIT];
  if (publicPaths.includes(pathname)) return;

  const loginPath = pathname.startsWith('/admin') ? PageEnum.ADMIN_LOGIN : PageEnum.PARENT_LOGIN;
  window.location.replace(loginPath);
}

export const Alova = createAlova({
  baseURL: apiUrl,
  statesHook: VueHook,
  // 关闭全局响应缓存：alova v3 默认对 GET 启用 5 分钟内存缓存，
  // 会导致写操作（如积分调整）成功后的重新拉取命中陈旧数据
  cacheFor: null,
  // 在开发环境开启缓存命中日志
  cacheLogger: process.env.NODE_ENV === 'development',
  requestAdapter: mockAdapter,
  beforeRequest(method) {
    // 会话由 Cookie（HttpOnly access/refresh token）维持：
    // 所有请求携带凭据并附加 CSRF 头，不再注入 token/localStorage 凭据
    method.config.credentials = 'include';
    const csrfToken = getCsrfToken();
    if (csrfToken) {
      method.config.headers[CSRF_HEADER_NAME] = csrfToken;
    }
    // 处理 api 请求前缀
    const isUrlStr = isUrl(method.url as string);
    if (!isUrlStr && urlPrefix) {
      method.url = `${urlPrefix}${method.url}`;
    }
    if (!isUrlStr && apiUrl && isString(apiUrl)) {
      method.url = `${apiUrl}${method.url}`;
    }
  },
  responded: {
    onSuccess: async (response, method) => {
      const res = (response.json && (await response.json())) || response.body;

      // 是否返回原生响应头 比如：需要获取响应头时使用该属性
      if (method.meta?.isReturnNativeResponse) {
        return res;
      }

      // HTTP 401：会话已失效，清理状态并跳转对应区域登录页（静默，与旧版一致）
      if (response.status === 401) {
        handleUnauthorized();
        throw new Error('未登录或会话已过期');
      }

      // 后端统一响应信封：{ code: string, message: string, data: T, request_id }
      const body = (res || {}) as { code?: string; message?: string; data?: unknown };
      const { code, message, data } = body;

      // 需要直接获取完整信封（code/message/data）时开启
      if (method.meta?.isTransformResponse === false) {
        return res;
      }

      if (code === SUCCESS_CODE) {
        return data;
      }

      // @ts-ignore
      const Message = window.$message;

      // 业务失败：优先后端 message，为空时按错误码映射
      const resolvedMessage = message || getErrorMessage(code ?? '') || '请求失败';
      // meta.quiet：页面自行展示错误（如登录页内联提示）时不弹全局消息
      if (!method.meta?.quiet) {
        Message?.error(resolvedMessage);
      }
      throw new Error(resolvedMessage);
    },
  },
});

// 项目，多个不同 api 地址，可导出多个实例
// export const AlovaTwo = createAlova({
//   baseURL: 'http://localhost:9001',
// });
