/**
 * 功能用例 STEP ↔ TEXT 双向同步（与后端 CaseStepSplitUtils 规则对齐的前端版）
 */
const TOP_MARKER = /(【\d+】|\[\d+]|\d+[.、])/g;

function normalizeHtmlBreaks(content: string): string {
  return content
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/p>/gi, '\n')
    .replace(/<p[^>]*>/gi, '')
    .replace(/<[^>]+>/g, '');
}

function splitByNewLine(content: string): string[] {
  const result = content
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean);
  return result.length ? result : [''];
}

function splitByTopMarker(content: string): string[] {
  const starts: number[] = [];
  const re = new RegExp(TOP_MARKER.source, 'g');
  let m = re.exec(content);
  while (m) {
    starts.push(m.index);
    m = re.exec(content);
  }
  if (!starts.length) return [content.trim()];
  const result: string[] = [];
  for (let i = 0; i < starts.length; i++) {
    const start = starts[i];
    const end = i + 1 < starts.length ? starts[i + 1] : content.length;
    let part = content.slice(start, end).trim();
    if (i === 0 && start > 0) {
      const prefix = content.slice(0, start).trim();
      if (prefix) part = prefix + part;
    }
    if (part) result.push(part);
  }
  return result.length ? result : [content.trim()];
}

/** 单元格拆分（对齐后端规则） */
export function splitCaseStepCell(cellContent?: string): string[] {
  if (!cellContent || !String(cellContent).trim()) return [''];
  const content = normalizeHtmlBreaks(String(cellContent)).trim();
  if (!TOP_MARKER.test(content)) {
    TOP_MARKER.lastIndex = 0;
    return splitByNewLine(content);
  }
  TOP_MARKER.lastIndex = 0;
  return splitByTopMarker(content);
}

export function joinCaseStepAsText(parts: string[]): string {
  return (parts || []).filter((p) => p != null).join('\n');
}

export function stepsToTextFields(steps: { step?: string; expected?: string; desc?: string; result?: string }[]): {
  textDescription: string;
  expectedResult: string;
} {
  const descs = steps.map((s) => s.step || s.desc || '');
  const results = steps.map((s) => s.expected || s.result || '');
  return {
    textDescription: joinCaseStepAsText(descs).replace(/\n/g, '<br/>'),
    expectedResult: joinCaseStepAsText(results).replace(/\n/g, '<br/>'),
  };
}

export function textFieldsToSteps(
  textDescription?: string,
  expectedResult?: string
): { id: string; step: string; expected: string; showStep: boolean; showExpected: boolean }[] {
  const descs = splitCaseStepCell(textDescription);
  const results = splitCaseStepCell(expectedResult);
  const len = Math.max(descs.length, results.length);
  const list: { id: string; step: string; expected: string; showStep: boolean; showExpected: boolean }[] = [];
  for (let i = 0; i < len; i++) {
    list.push({
      id: `${Date.now()}-${i}-${Math.random().toString(36).slice(2, 8)}`,
      step: i < descs.length ? descs[i] : '',
      expected: i < results.length ? results[i] : '',
      showStep: false,
      showExpected: false,
    });
  }
  return list.length ? list : [{ id: `${Date.now()}`, step: '', expected: '', showStep: false, showExpected: false }];
}
