/**
 * 浏览器直接加载的媒体 URL（img/src）需带上与 Axios 一致的 API 前缀。
 * Axios baseURL = origin/{VITE_API_BASE_URL}，而富文本里 historically 存的是 /bug/...、/attachment/...，
 * 生产环境会打到前端静态资源导致裂图。
 */
export function getApiBaseSegment(): string {
  const raw = String(import.meta.env.VITE_API_BASE_URL ?? 'api');
  return (
    raw
      .replace(/['"]/g, '')
      .trim()
      .replace(/^\/+|\/+$/g, '') || 'api'
  );
}

/** 给以 / 开头的业务路径补上 /{apiBase} 前缀；已带前缀或绝对/blob/data 地址不改 */
export function withApiPrefix(path: string): string {
  if (!path) return path;
  if (/^(https?:|blob:|data:)/i.test(path)) return path;
  const prefix = getApiBaseSegment();
  const normalized = path.startsWith('/') ? path : `/${path}`;
  if (normalized === `/${prefix}` || normalized.startsWith(`/${prefix}/`)) {
    return normalized;
  }
  return `/${prefix}${normalized}`;
}

/** 只读富文本 HTML：原图 + API 前缀，修复历史裂图 */
export function rewriteRichTextMediaHtml(html: string): string {
  if (!html || html === '-') return html;
  const prefix = getApiBaseSegment();
  let result = html
    // 缩略图 → 原图
    .replace(
      /(\/(?:bug\/attachment\/preview\/md|attachment\/download\/file|test-plan\/report\/preview\/md)\/[^"'\\\s]+\/)true/gi,
      '$1false'
    );

  const mediaPathRe =
    /(src\s*=\s*["'])\/(?!PREFIX\/)((?:bug\/attachment\/preview\/md|attachment\/download\/file|test-plan\/report\/preview\/md)\/)/gi;
  result = result.replace(new RegExp(mediaPathRe.source.replace('PREFIX', prefix), 'gi'), `$1/${prefix}/$2`);
  return result;
}
