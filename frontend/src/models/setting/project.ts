// 项目列表项
export interface ProjectListItem {
  id: string;
  num: number;
  organizationId: string;
  name: string;
  description: string;
  createTime: number;
  updateTime: number;
  updateUser: string;
  createUser: string;
  deleteTime: number;
  deleted: boolean;
  deleteUser: string;
  enable: boolean;
  /** 系统默认项目（米多公司默认项目） */
  isDefault?: boolean;
  moduleIds: string[];
  moduleSetting: string;
}
