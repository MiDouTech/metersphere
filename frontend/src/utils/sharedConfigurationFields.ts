export interface SecretField {
  name: string;
  value: string;
}
export function encodeSecretFields(type: string, fields: SecretField[]): string {
  const normalized = fields.map((field) => ({ name: field.name.trim(), value: field.value }));
  const names = new Set(normalized.map((field) => field.name));
  if (
    !['ACCOUNT_PASSWORD', 'OTHER'].includes(type) ||
    !normalized.length ||
    normalized.length > 20 ||
    names.size !== normalized.length ||
    normalized.some(
      (field) => !field.name || field.name.length > 64 || !field.value.trim() || field.value.length > 100000
    ) ||
    (type === 'ACCOUNT_PASSWORD' && (!names.has('username') || !names.has('password')))
  ) {
    throw new Error('请填写1至20个有效字段，字段名不能重复；账号和密码为必填。');
  }
  return JSON.stringify({ fields: normalized });
}
