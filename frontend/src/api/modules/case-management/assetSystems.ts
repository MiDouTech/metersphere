import MSR from '@/api/http/index';

export interface AssetSystemModule {
  id: string;
  name: string;
  parentId: string;
  caseCount: number;
}
export interface AssetSystem {
  id: string;
  name: string;
  caseCount: number;
  modules: AssetSystemModule[];
}
export const getAssetSystems = () =>
  MSR.get<{ systems: AssetSystem[]; unclassifiedCount: number }>({ url: '/case-asset/systems' });
export const saveAssetSystem = (name: string, id?: string) =>
  id
    ? MSR.put({ url: `/case-asset/systems/${id}`, data: { name } })
    : MSR.post({ url: '/case-asset/systems', data: { name } });
export const deleteAssetSystem = (id: string) => MSR.delete({ url: `/case-asset/systems/${id}` });
export const saveAssetSystemModule = (systemId: string, name: string, parentId: string, id?: string) =>
  id
    ? MSR.put({ url: `/case-asset/systems/${systemId}/modules/${id}`, data: { name } })
    : MSR.post({ url: `/case-asset/systems/${systemId}/modules`, data: { name, parentId } });
export const deleteAssetSystemModule = (systemId: string, id: string) =>
  MSR.delete({ url: `/case-asset/systems/${systemId}/modules/${id}` });
export const classifyAssetCases = (ids: string[], systemId?: string, moduleId?: string) =>
  MSR.post<{ count: number }>({ url: '/case-asset/system-classification', data: { ids, systemId, moduleId } });
