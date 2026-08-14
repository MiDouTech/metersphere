package io.metersphere.system.event;

public record TestReportGeneratedEvent(String eventId, String reportId, String testPlanId, String projectId,
                                       String reportName, String generatorUserId, String generationMode,
                                       long generatedAt) {
}
