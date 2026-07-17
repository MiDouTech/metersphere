package io.metersphere.functional.mapper;

import io.metersphere.functional.domain.FunctionalCaseXmindFile;
import org.apache.ibatis.annotations.Param;

public interface FunctionalCaseXmindFileMapper {

    FunctionalCaseXmindFile selectByPrimaryKey(@Param("id") String id);

    int insert(FunctionalCaseXmindFile record);

    int updateByPrimaryKeySelective(FunctionalCaseXmindFile record);

    int deleteByPrimaryKey(@Param("id") String id);
}
