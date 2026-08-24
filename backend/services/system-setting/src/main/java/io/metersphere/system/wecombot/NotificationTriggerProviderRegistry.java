package io.metersphere.system.wecombot;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class NotificationTriggerProviderRegistry {
    public record Provider(String notificationType, String triggerType, List<String> scopes, List<String> variables) {
    }

    private final Map<String, Provider> providers = Map.of(
            "BUG_EXPECTED_RESOLUTION_DUE", new Provider("BUG_EXPECTED_RESOLUTION_DUE", "DEADLINE",
                    List.of("SYSTEM", "PROJECT"), List.of("bugNum", "bugTitle", "bugStatus", "bugHandlerNames", "bugCreatorName", "expectedResolveTime", "remainingTime", "projectName", "resourceUrl")),
            "TEST_REPORT_GENERATED", new Provider("TEST_REPORT_GENERATED", "EVENT",
                    List.of("SYSTEM", "PROJECT"), List.of("projectName", "testPlanName", "reportName", "reportGeneratorName", "reportSummary", "reportUrl", "generatedAt")),
            "CUSTOM_CRON", new Provider("CUSTOM_CRON", "CRON",
                    List.of("SYSTEM", "PROJECT"), List.of("customTitle", "customContent", "now"))
    );

    public Provider require(String type) {
        Provider provider = providers.get(type);
        if (provider == null) throw new io.metersphere.sdk.exception.MSException("Unsupported notification type");
        return provider;
    }

    public List<Provider> list() { return List.copyOf(providers.values()); }

    public List<WecomBotModels.TemplateVariable> variables(String type) {
        Provider provider = require(type);
        return java.util.stream.Stream.concat(provider.variables().stream(), java.util.stream.Stream.of("ruleName"))
                .distinct().map(this::describe).toList();
    }

    private WecomBotModels.TemplateVariable describe(String key) {
        return switch (key) {
            case "bugNum" -> variable(key, "缺陷编号", "缺陷的业务编号", "BUG-001");
            case "bugTitle" -> variable(key, "缺陷名称", "缺陷标题", "登录接口返回异常");
            case "bugStatus" -> variable(key, "缺陷状态", "当前缺陷处理状态名称", "待修复");
            case "bugHandlerNames" -> variable(key, "处理人", "当前缺陷负责人，多个负责人以逗号分隔", "张三, 李四");
            case "bugCreatorName" -> variable(key, "创建人", "缺陷创建人", "王五");
            case "expectedResolveTime" -> variable(key, "预计解决时间", "缺陷计划解决时间", "2026-08-21 18:00");
            case "remainingTime" -> variable(key, "剩余时间", "距预计解决时间的剩余时长", "2小时");
            case "projectName" -> variable(key, "项目名称", "当前资源所属项目名称", "MeterSphere");
            case "resourceUrl" -> variable(key, "缺陷链接", "缺陷详情页访问地址", "https://example.com/bug/1");
            case "testPlanName" -> variable(key, "测试计划", "生成报告的测试计划名称", "版本验收计划");
            case "reportName" -> variable(key, "报告名称", "本次生成的测试报告名称", "版本验收报告");
            case "reportGeneratorName" -> variable(key, "生成人", "报告生成操作人", "张三");
            case "reportSummary" -> variable(key, "报告摘要", "测试报告中的摘要内容", "通过率 98%");
            case "reportUrl" -> variable(key, "报告链接", "测试报告详情页访问地址", "https://example.com/report/1");
            case "generatedAt" -> variable(key, "生成时间", "报告实际生成时间", "2026-08-21 18:00");
            case "customTitle" -> variable(key, "自定义标题", "通用通知规则名称", "每日提醒");
            case "customContent" -> variable(key, "自定义内容", "通用通知的扩展内容", "请及时处理待办事项");
            case "now" -> variable(key, "触发时间", "通知实际触发时间", "2026-08-21 09:00");
            case "ruleName" -> variable(key, "规则名称", "当前通知规则名称", "缺陷到期提醒");
            default -> variable(key, key, "系统提供的模板变量", "-");
        };
    }

    private WecomBotModels.TemplateVariable variable(String key, String name, String description, String example) {
        return new WecomBotModels.TemplateVariable(key, name, description, example);
    }
}
