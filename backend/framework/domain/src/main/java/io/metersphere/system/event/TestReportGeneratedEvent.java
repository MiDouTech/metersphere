package io.metersphere.system.event;

public record TestReportGeneratedEvent(String eventId, String reportId, String testPlanId, String projectId,
                                       String reportName, String generatorUserId, String generationMode,
                                       long generatedAt, String reportType) {
    public static final String TYPE_TEST_PLAN = "TEST_PLAN";
    public static final String TYPE_FUNCTIONAL = "FUNCTIONAL";

    public TestReportGeneratedEvent(String eventId, String reportId, String testPlanId, String projectId,
                                    String reportName, String generatorUserId, String generationMode,
                                    long generatedAt) {
        this(eventId, reportId, testPlanId, projectId, reportName, generatorUserId, generationMode,
                generatedAt, TYPE_TEST_PLAN);
    }
}
