package io.metersphere.functional.mapper;

import io.metersphere.functional.domain.FunctionalTestReport;
import io.metersphere.functional.dto.FunctionalTestReportBugCountDTO;
import io.metersphere.functional.dto.FunctionalTestReportOpenBugDTO;
import io.metersphere.functional.dto.FunctionalTestReportResultCountDTO;
import io.metersphere.functional.dto.FunctionalTestReportRiskCaseDTO;
import io.metersphere.functional.request.FunctionalTestReportPageRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtFunctionalTestReportMapper {

    List<FunctionalTestReport> list(@Param("request") FunctionalTestReportPageRequest request);

    List<FunctionalTestReportResultCountDTO> countExecByProject(@Param("projectId") String projectId);

    List<FunctionalTestReportResultCountDTO> countExecByPlan(@Param("planId") String planId);

    List<FunctionalTestReportRiskCaseDTO> listRiskCasesByProject(@Param("projectId") String projectId);

    List<FunctionalTestReportRiskCaseDTO> listRiskCasesByPlan(@Param("planId") String planId);

    List<FunctionalTestReportBugCountDTO> countBugHandlerStatusByPlan(@Param("planId") String planId);

    List<FunctionalTestReportBugCountDTO> countBugTypeByPlan(@Param("planId") String planId);

    List<FunctionalTestReportOpenBugDTO> listOpenBugsByPlan(@Param("planId") String planId);
}
