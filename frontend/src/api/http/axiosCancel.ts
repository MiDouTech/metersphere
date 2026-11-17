import axios from 'axios';

import { isFunction } from '@/utils/is';

import type { AxiosRequestConfig, Canceler } from 'axios';

const pendingMap = new Map<string, { cancel: Canceler; token: AxiosRequestConfig['cancelToken'] }>();

export const getPendingUrl = (config: AxiosRequestConfig) => [config.method, config.url].join('&');

export class AxiosCanceler {
  /**
   * 添加请求
   * @param {Object} config
   */
  addPending(config: AxiosRequestConfig) {
    this.removePending(config);
    const url = getPendingUrl(config);
    if (!config.cancelToken) {
      let cancelRequest!: Canceler;
      config.cancelToken = new axios.CancelToken((cancel) => {
        cancelRequest = cancel;
      });
      pendingMap.set(url, { cancel: cancelRequest, token: config.cancelToken });
    }
  }

  /** A completed older request must never cancel a newer request to the same URL. */
  completePending(config: AxiosRequestConfig) {
    const url = getPendingUrl(config);
    if (pendingMap.get(url)?.token === config.cancelToken) pendingMap.delete(url);
  }

  /**
   * @description: 清理全部pending中的请求
   */
  removeAllPending() {
    pendingMap.forEach(({ cancel }) => {
      if (cancel && isFunction(cancel)) {
        cancel();
      }
    });
    pendingMap.clear();
  }

  /**
   * 取消并移除指定请求
   * @param {Object} config
   */
  removePending(config: AxiosRequestConfig) {
    const url = getPendingUrl(config);

    if (pendingMap.has(url)) {
      // 根据标识找到pending中对应的请求并取消
      const cancel = pendingMap.get(url)?.cancel;
      if (cancel && isFunction(cancel)) {
        cancel(url);
      }
      pendingMap.delete(url);
    }
  }

  /**
   * @description: 重置pending列表
   */
  static reset(): void {
    pendingMap.clear();
  }
}
