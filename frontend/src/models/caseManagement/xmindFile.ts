import type { TableQueryParams } from '@/models/common';

export interface XmindFileItem {
  id: string;
  projectId: string;
  name: string;
  originalName: string;
  size: number;
  createTime: number;
  updateTime: number;
  createUser: string;
  updateUser: string;
}

export interface XmindFilePageRequest extends TableQueryParams {
  projectId: string;
}

export interface XmindFileUploadRequest {
  projectId: string;
  name?: string;
}

export interface XmindFileRenameRequest {
  id: string;
  name: string;
}
