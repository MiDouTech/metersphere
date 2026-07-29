import { useI18n } from '@/hooks/useI18n';

type BugFieldTipSource = {
  internalFieldKey?: string;
  fieldName?: string;
};

/**
 * 严重程度 / 缺陷类型字段悬浮说明（匹配内置 key 或展示名）
 */
export default function resolveBugFieldTooltip(item: BugFieldTipSource): string | undefined {
  const { t } = useI18n();
  const key = item.internalFieldKey || '';
  const name = item.fieldName || '';

  if (key === 'bug_degree' || name === '严重程度' || name === 'Bug Degree') {
    return t('bugManagement.fieldTip.severity');
  }
  if (key === 'bug_type' || name === '缺陷类型' || name === 'Bug Type') {
    return t('bugManagement.fieldTip.bugType');
  }
  return undefined;
}
