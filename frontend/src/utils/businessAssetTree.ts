export interface BusinessClassification {
  id: string;
  systemKey: string;
  moduleKey: string;
  flowKey: string;
  pageKeys: string[];
  branchCount: number;
}
export interface BusinessTreeNode {
  key: string;
  title: string;
  children?: BusinessTreeNode[];
}

export function matchesBusinessCategory(asset: BusinessClassification, key: string): boolean {
  if (!key) return true;
  try {
    const path = JSON.parse(key) as string[];
    if (!Array.isArray(path) || path.length > 4 || path.some((value) => typeof value !== 'string')) return false;
    const values = [asset.systemKey, asset.moduleKey, asset.flowKey];
    return path.every((value, index) =>
      index === 3 ? (asset.pageKeys.length ? asset.pageKeys : ['']).includes(value) : values[index] === value
    );
  } catch {
    return false;
  }
}

export function businessAssetTree(assets: BusinessClassification[], path: string[] = []): BusinessTreeNode[] {
  const level = path.length;
  if (level >= 4) return [];
  const subset = assets.filter((asset) => matchesBusinessCategory(asset, JSON.stringify(path)));
  const values = subset.flatMap((asset) => {
    if (level === 3) return asset.pageKeys.length ? asset.pageKeys : [''];
    return [[asset.systemKey, asset.moduleKey, asset.flowKey][level]];
  });
  return [...new Set(values)].sort().map((value) => {
    const next = [...path, value];
    const key = JSON.stringify(next);
    const rows = subset.filter((asset) => matchesBusinessCategory(asset, key));
    const title = value || (level === 2 ? '页面操作' : '待归类');
    const branches = rows.reduce((sum, row) => sum + row.branchCount, 0);
    return {
      key,
      title: `${title}（${rows.length} 文档 / ${branches} 分支）`,
      children: businessAssetTree(rows, next),
    };
  });
}
