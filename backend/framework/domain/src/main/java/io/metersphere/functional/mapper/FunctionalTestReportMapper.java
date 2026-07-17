package io.metersphere.functional.mapper;

import io.metersphere.functional.domain.FunctionalTestReport;
import org.apache.ibatis.annotations.Param;

public interface FunctionalTestReportMapper {

    FunctionalTestReport selectByPrimaryKey(@Param("id") String id);

    int insert(FunctionalTestReport record);

    int updateByPrimaryKeySelective(FunctionalTestReport record);

    int deleteByPrimaryKey(@Param("id") String id);
}
