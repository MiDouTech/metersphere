# task002 P0：全局用例查询与权限

## 工作内容

- 将用例查询从项目级改为工作空间级服务端分页。
- 支持项目、系统、目录、未关联项目、待分类等筛选。
- 建立统一的数据权限过滤组件，供列表、统计和导出复用。
- 增加工作空间无项目用例的读写权限。
- 有项目用例继续校验所属项目权限。

## 接口参数

```text
workspaceId
dimension=PROJECT|SYSTEM
projectIds[]
systemId
systemModuleId
unassignedProject
unclassifiedSystem
keyword
page/pageSize
```

## 验收标准

- 不传项目 ID 可以分页查询所有有权用例。
- 无项目用例只对具有工作空间用例权限的用户可见。
- 项目目录、系统目录计数与列表权限结果一致。
- 搜索、导出不能获取列表中无权查看的数据。
- 典型数据量下查询满足项目既定性能标准。

