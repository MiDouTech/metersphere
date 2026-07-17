package io.metersphere.plan.mapper;

import io.metersphere.plan.domain.TestPlanDocument;
import org.apache.ibatis.annotations.Param;

public interface TestPlanDocumentMapper {

    TestPlanDocument selectByPrimaryKey(@Param("id") String id);

    TestPlanDocument selectByTestPlanId(@Param("testPlanId") String testPlanId);

    int insert(TestPlanDocument record);

    int updateByPrimaryKeySelective(TestPlanDocument record);

    int deleteByTestPlanId(@Param("testPlanId") String testPlanId);
}
