import type { TestPlanDocumentTemplateMeta } from '@/models/testPlan/document';

function cell(text: string) {
  return text || '待填写';
}

function infoLine(label: string, value: string) {
  return `<p>${label}：${cell(value)}</p>`;
}

function emptyParagraph() {
  return '<p>待填写</p>';
}

function sectionTitle(title: string) {
  return `<h2>${title}</h2>`;
}

/**
 * 生成标准 14 节测试计划文档 HTML 模板（富文本，纯文本行，不含表格）
 * 重置为模板 / 首次无文档时使用；templateMeta 自动填充文档信息
 */
export default function buildTestPlanDocumentTemplate(meta?: TestPlanDocumentTemplateMeta): string {
  const projectName = meta?.projectName || '';
  const planName = meta?.planName || '';
  const author = meta?.author || '';
  const date = meta?.date || '';
  const docNo = meta?.docNo || '';

  const parts: string[] = [
    sectionTitle('1. 文档信息'),
    infoLine('编号', docNo),
    infoLine('版本', 'V1.0'),
    infoLine('所属项目', projectName),
    infoLine('测试模块', planName),
    infoLine('编制日期', date),
    infoLine('编制人', author),
    infoLine('审核人', ''),

    sectionTitle('2. 项目背景'),
    emptyParagraph(),

    sectionTitle('3. 测试目标'),
    emptyParagraph(),

    sectionTitle('4. 测试范围'),
    '<h3>4.1 范围内</h3>',
    emptyParagraph(),
    '<h3>4.2 范围外</h3>',
    emptyParagraph(),

    sectionTitle('5. 测试策略'),
    emptyParagraph(),

    sectionTitle('6. 测试重点'),
    '<h3>6.1 主流程</h3>',
    emptyParagraph(),
    '<h3>6.2 规则与边界</h3>',
    emptyParagraph(),
    '<h3>6.3 权限与安全</h3>',
    emptyParagraph(),

    sectionTitle('7. 测试环境与数据'),
    '<p>测试环境：说明 待填写；备注 待填写</p>',
    '<p>测试数据：说明 待填写；备注 待填写</p>',

    sectionTitle('8. 准入、暂停与退出标准'),
    infoLine('准入标准', ''),
    infoLine('暂停标准', ''),
    infoLine('退出标准', ''),

    sectionTitle('9. 测试进度'),
    '<p>准备：计划开始 待填写；计划结束 待填写；说明 待填写</p>',
    '<p>执行：计划开始 待填写；计划结束 待填写；说明 待填写</p>',
    '<p>收尾：计划开始 待填写；计划结束 待填写；说明 待填写</p>',

    sectionTitle('10. 人员与职责'),
    `<p>测试负责人：人员 ${cell(author)}；职责 待填写</p>`,
    '<p>测试执行：人员 待填写；职责 待填写</p>',

    sectionTitle('11. 缺陷管理'),
    emptyParagraph(),

    sectionTitle('12. 风险与应对'),
    '<p>风险：待填写；影响：待填写；应对措施：待填写</p>',

    sectionTitle('13. 测试交付物'),
    `<p>测试计划：说明 ${cell(planName)}；负责人 ${cell(author)}</p>`,
    '<p>测试报告：说明 待填写；负责人 待填写</p>',

    sectionTitle('14. 审批记录'),
    '<p>审批人：待填写；角色：待填写；意见：待填写；日期：待填写</p>',
  ];

  return parts.join('');
}
