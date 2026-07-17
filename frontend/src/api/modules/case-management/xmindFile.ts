import type { MinderJson } from '@/components/pure/ms-minder-editor/props';

import MSR from '@/api/http/index';
import {
  XmindFileDeleteUrl,
  XmindFileDownloadUrl,
  XmindFilePageUrl,
  XmindFilePreviewUrl,
  XmindFileRenameUrl,
  XmindFileUploadUrl,
} from '@/api/requrls/case-management/xmindFile';

import type {
  XmindFileItem,
  XmindFilePageRequest,
  XmindFileRenameRequest,
  XmindFileUploadRequest,
} from '@/models/caseManagement/xmindFile';

/** 分页列表 */
export function getXmindFilePage(data: XmindFilePageRequest) {
  return MSR.post<{ list: XmindFileItem[]; total: number }>({ url: XmindFilePageUrl, data });
}

/** 上传（仅存文件资产） */
export function uploadXmindFile(request: XmindFileUploadRequest, file: File) {
  return MSR.uploadFile({ url: XmindFileUploadUrl }, { request, fileList: [file] }, 'file');
}

/** 重命名 */
export function renameXmindFile(data: XmindFileRenameRequest) {
  return MSR.post<XmindFileItem>({ url: XmindFileRenameUrl, data });
}

/** 下载 */
export function downloadXmindFile(id: string) {
  return MSR.get({ url: `${XmindFileDownloadUrl}/${id}`, responseType: 'blob' }, { isTransformResponse: false });
}

/** 在线预览 MinderJson */
export function previewXmindFile(id: string) {
  return MSR.get<MinderJson>({ url: `${XmindFilePreviewUrl}/${id}` });
}

/** 删除 */
export function deleteXmindFile(id: string) {
  return MSR.get({ url: `${XmindFileDeleteUrl}/${id}` });
}
