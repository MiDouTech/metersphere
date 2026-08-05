/**
 * 缺陷状态展示样式映射（列表/回收站/工作台等入口复用）
 */
export default function getBugStatusClass(status?: string): string {
  const normalizedStatus = String(status || '').toLowerCase();
  if (
    normalizedStatus.includes('新建') ||
    normalizedStatus.includes('待处理') ||
    normalizedStatus.includes('open') ||
    normalizedStatus.includes('new')
  ) {
    return 'is-new';
  }
  if (
    normalizedStatus.includes('处理中') ||
    normalizedStatus.includes('处理') ||
    normalizedStatus.includes('进行中') ||
    normalizedStatus.includes('process') ||
    normalizedStatus.includes('progress')
  ) {
    return 'is-processing';
  }
  if (
    normalizedStatus.includes('挂起') ||
    normalizedStatus.includes('掛起') ||
    normalizedStatus.includes('suspended') ||
    normalizedStatus.includes('suspend')
  ) {
    return 'is-suspended';
  }
  if (
    normalizedStatus.includes('已解决') ||
    normalizedStatus.includes('解决') ||
    normalizedStatus.includes('resolved') ||
    normalizedStatus.includes('fixed')
  ) {
    return 'is-resolved';
  }
  if (
    normalizedStatus.includes('已关闭') ||
    normalizedStatus.includes('关闭') ||
    normalizedStatus.includes('非缺陷') ||
    normalizedStatus.includes('closed') ||
    normalizedStatus.includes('invalid')
  ) {
    return 'is-muted';
  }
  return 'is-default';
}
