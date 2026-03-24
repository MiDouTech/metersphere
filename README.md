<p align="center"><a href="https://metersphere.io"><img src="https://metersphere.oss-cn-hangzhou.aliyuncs.com/img/MeterSphere-%E7%B4%AB%E8%89%B2.png" alt="MeterSphere" width="300" /></a></p>
<h3 align="center">新一代的开源持续测试工具</h3>
<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0.html"><img src="https://shields.io/github/license/metersphere/metersphere?color=%231890FF" alt="License: GPL v3"></a>
  <a href="https://www.codacy.com/gh/metersphere/metersphere/dashboard?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=metersphere/metersphere&amp;utm_campaign=Badge_Grade"><img src="https://app.codacy.com/project/badge/Grade/da67574fd82b473992781d1386b937ef" alt="Codacy"></a>
  <a href="https://github.com/metersphere/metersphere/releases"><img src="https://img.shields.io/github/v/release/metersphere/metersphere" alt="GitHub release"></a>
  <a href="https://github.com/metersphere/metersphere"><img src="https://img.shields.io/github/stars/metersphere/metersphere?color=%231890FF&style=flat-square" alt="Stars"></a>
  <a href="https://hub.docker.com/r/metersphere/metersphere-ce-allinone"><img src="https://img.shields.io/docker/pulls/metersphere/metersphere-ce-allinone?label=downloads" alt="Download"></a>
  <a href="https://gitee.com/fit2cloud-feizhiyun/MeterSphere"><img src="https://gitee.com/fit2cloud-feizhiyun/MeterSphere/badge/star.svg?theme=gvp" alt="Gitee Stars"></a>
  <a href="https://gitcode.com/feizhiyun/MeterSphere"><img src="https://gitcode.com/feizhiyun/MeterSphere/star/badge.svg" alt="GitCode Stars"></a><br>
</p>
<p align="center">
  <a href="https://trendshift.io/repositories/1557" target="_blank"><img src="https://trendshift.io/api/badge/repositories/1557" alt="metersphere%2Fmetersphere | Trendshift" style="width: 250px; height: 55px;" width="250" height="55"/></a>
</p>
<hr />

MeterSphere 是新一代的开源持续测试工具，内置 AI 助手，让软件测试工作更简单、更高效，不再成为持续交付的瓶颈。

-   **AI 赋能**：内置基于大模型的 AI 助手，支持 AI 生成功能用例、接口用例等，提升测试效率；
-   **测试管理**：从测试用例管理，到测试计划执行、缺陷管理、测试报告生成，使用体验远超传统测试管理工具；
-   **接口测试**：集 Postman 的易用与 JMeter 的灵活于一体，接口调试、接口定义、接口 Mock、场景自动化、接口报告，端到端支持；
-   **团队协作**：采用“系统-组织-项目”分层设计理念，帮助用户摆脱单机测试工具的束缚，方便快捷地开展团队协作；
-   **插件体系**：提供各种类别的插件，快速实现 MeterSphere 测试能力的扩展以及与 DevOps 流水线的集成。

## 快速开始

你可以通过 [1Panel 应用商店](https://metersphere.io/docs/v3.x/installation/1Panel/) 快速部署 MeterSphere。

如用于生产环境，推荐使用 [离线安装包方式](https://metersphere.io/docs/v3.x/installation/offline_installation/) 进行安装部署。

如果你需要安装插件，请访问 [MeterSphere 插件市场](https://apps.fit2cloud.com/metersphere)。

## UI 展示

<table style="border-collapse: collapse; border: 1px solid black;">
  <tr>
    <td style="padding: 5px;background-color:#fff;"><img src= "https://github.com/metersphere/metersphere/assets/23045261/e330db63-ea48-43b5-9645-b143c3326632" alt="MeterSphere Demo1" /></td>
    <td style="padding: 5px;background-color:#fff;"><img src= "https://github.com/metersphere/metersphere/assets/23045261/315a13f6-6565-498d-ab62-6d5b46d49591" alt="MeterSphere Demo2" /></td>
  </tr>
  <tr>
    <td style="padding: 5px;background-color:#fff;"><img src= "https://github.com/metersphere/metersphere/assets/23045261/785f7c05-430c-4eab-a0c5-0661bc177df0" alt="MeterSphere Demo3" /></td>
    <td style="padding: 5px;background-color:#fff;"><img src= "https://github.com/metersphere/metersphere/assets/23045261/a53dd241-0140-43e4-83ba-95f0f0aeccc5" alt="MeterSphere Demo4" /></td>
  </tr>
  <tr>
    <td style="padding: 5px;background-color:#fff;"><img src= "https://github.com/metersphere/metersphere/assets/23045261/fc09f2bc-a822-4c8c-ba58-c8e55f362fa3" alt="MeterSphere Demo5" /></td>
    <td style="padding: 5px;background-color:#fff;"><img src= "https://github.com/metersphere/metersphere/assets/23045261/ed689d96-78fc-4e21-a29b-49054291dc59" alt="MeterSphere Demo6" /></td>
  </tr>
  <tr>
    <td style="padding: 5px;background-color:#fff;"><img src= "https://github.com/metersphere/metersphere/assets/23045261/8b468704-3741-4f73-a86c-f224f15aeba2" alt="MeterSphere Demo7" /></td>
    <td style="padding: 5px;background-color:#fff;"><img src= "https://github.com/metersphere/metersphere/assets/23045261/023dad1b-37c6-480c-a32e-4c71dd1010d2" alt="MeterSphere Demo8" /></td>
  </tr>
</table>

## 版本说明

MeterSphere 当前最新版本为 V3。MeterSphere V3 分为社区版和企业版，详情请参见：[MeterSphere 产品版本对比](https://metersphere.io/pricing.html)。

注：MeterSphere V1 和 V2 版本已停止维护。

## 技术栈

-   后端: [Spring Boot](https://www.tutorialspoint.com/spring_boot/spring_boot_introduction.htm)
-   前端: [Vue.js](https://vuejs.org/)
-   中间件: [MySQL](https://www.mysql.com/), [Kafka](https://kafka.apache.org/), [MinIO](https://min.io/), [Redis](https://redis.com/)
-   基础设施: [Docker](https://www.docker.com/)
-   测试引擎: [JMeter](https://jmeter.apache.org/)

## 飞致云的其他明星项目

- [1Panel](https://github.com/1panel-dev/1panel/) - 现代化、开源的 Linux 服务器运维管理面板
- [JumpServer](https://github.com/jumpserver/jumpserver/) - 广受欢迎的开源堡垒机
- [DataEase](https://github.com/dataease/dataease/) - 人人可用的开源 BI 工具
- [SQLBot](https://github.com/dataease/SQLBot) - 基于大模型和 RAG 的开源智能问数系统
- [MaxKB](https://github.com/1panel-dev/MaxKB/) - 强大易用的企业级智能体平台
- [Cordys CRM](https://github.com/1Panel-dev/CordysCRM) - 新一代的开源 AI CRM 系统
- [Halo](https://github.com/halo-dev/halo/) - 强大易用的开源建站工具

## License

本仓库遵循 [FIT2CLOUD Open Source License](LICENSE) 开源协议，该许可证本质上是 GPLv3，但有一些额外的限制。

你可以基于 MeterSphere 的源代码进行二次开发，但是需要遵守以下规定：

- 不能替换和修改 MeterSphere 的 Logo 和版权信息；
- 二次开发后的衍生作品必须遵守 GPL V3 的开源义务。
