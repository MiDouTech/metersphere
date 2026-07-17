import type { TestPlanDocumentTemplateMeta } from '@/models/testPlan/document';

function cell(text: string) {
  return text || '待填写';
}

function infoRow(label: string, value: string) {
  return `<tr><th colspan="1"><p>${label}</p></th><td colspan="1"><p>${cell(value)}</p></td></tr>`;
}

function emptyParagraph() {
  return '<p>待填写</p>';
}

function sectionTitle(title: string) {
  return `<h2>${title}</h2>`;
}

function simpleTable(headers: string[], rows: string[][]) {
  const head = `<tr>${headers.map((h) => `<th colspan="1"><p>${h}</p></th>`).join('')}</tr>`;
  const body = rows
    .map((row) => `<tr>${row.map((c) => `<td colspan="1"><p>${cell(c)}</p></td>`).join('')}</tr>`)
    .join('');
  return `<table><tbody>${head}${body}</tbody></table>`;
}

/**
 * 生成标准 14 节测试计划文档 HTML 模板（富文本）
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
    `<table><tbody>${[
      infoRow('编号', docNo),
      infoRow('版本', 'V1.0'),
      infoRow('所属项目', projectName),
      infoRow('测试模块', planName),
      infoRow('编制日期', date),
      infoRow('编制人', author),
      infoRow('审核人', ''),
    ].join('')}</tbody></table>`,

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
    simpleTable(
      ['环境', '说明', '备注'],
      [
        ['测试环境', '', ''],
        ['测试数据', '', ''],
      ]
    ),

    sectionTitle('8. 准入、暂停与退出标准'),
    simpleTable(
      ['类型', '标准说明'],
      [
        ['准入标准', ''],
        ['暂停标准', ''],
        ['退出标准', ''],
      ]
    ),

    sectionTitle('9. 测试进度'),
    simpleTable(
      ['阶段', '计划开始', '计划结束', '说明'],
      [
        ['准备', '', '', ''],
        ['执行', '', '', ''],
        ['收尾', '', '', ''],
      ]
    ),

    sectionTitle('10. 人员与职责'),
    simpleTable(
      ['角色', '人员', '职责'],
      [
        ['测试负责人', author, ''],
        ['测试执行', '', ''],
      ]
    ),

    sectionTitle('11. 缺陷管理'),
    emptyParagraph(),

    sectionTitle('12. 风险与应对'),
    simpleTable(['风险', '影响', '应对措施'], [['', '', '']]),

    sectionTitle('13. 测试交付物'),
    simpleTable(
      ['交付物', '说明', '负责人'],
      [
        ['测试计划', planName, author],
        ['测试报告', '', ''],
      ]
    ),

    sectionTitle('14. 审批记录'),
    simpleTable(['审批人', '角色', '意见', '日期'], [['', '', '', '']]),
  ];

  return parts.join('');
}
