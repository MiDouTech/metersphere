package io.metersphere.functional.mapper;

import io.metersphere.functional.domain.FunctionalCaseXmindFile;
import io.metersphere.functional.request.FunctionalCaseXmindFilePageRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtFunctionalCaseXmindFileMapper {

    List<FunctionalCaseXmindFile> list(@Param("request") FunctionalCaseXmindFilePageRequest request);
}
