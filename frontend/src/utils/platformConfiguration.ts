/** UI validation mirrors the server's URL/Origin contract; server authorization remains authoritative. */
export function platformUrl(value: string, originOnly = false): URL {
  let url: URL;
  try {
    url = new URL(value.trim());
  } catch {
    throw new Error('请填写完整 HTTP/HTTPS 地址，例如 https://test.example.com');
  }
  if (!['http:', 'https:'].includes(url.protocol) || !url.hostname || url.username || url.password) {
    throw new Error('请填写不含账号密码的完整 HTTP/HTTPS 地址');
  }
  if (originOnly && ((url.pathname !== '/' && url.pathname !== '') || url.search || url.hash)) {
    throw new Error('允许 Origin 只能包含协议、主机及端口，不包含路径或查询参数');
  }
  return url;
}

export function validateLoginJson(value: string, label: string): void {
  let object: unknown;
  try {
    object = JSON.parse(value);
  } catch {
    throw new Error(`${label}必须是合法 JSON`);
  }
  if (!object || typeof object !== 'object' || Array.isArray(object)) throw new Error(`${label}必须是 JSON 对象`);
}
