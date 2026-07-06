package io.metersphere.system.mapper;

import io.metersphere.system.domain.OrgSyncLog;
import io.metersphere.system.domain.OrgSyncLogExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface OrgSyncLogMapper {
    long countByExample(OrgSyncLogExample example);

    int deleteByExample(OrgSyncLogExample example);

    int deleteByPrimaryKey(String id);

    int insert(OrgSyncLog record);

    int insertSelective(OrgSyncLog record);

    List<OrgSyncLog> selectByExample(OrgSyncLogExample example);

    OrgSyncLog selectByPrimaryKey(String id);

    int updateByExampleSelective(@Param("record") OrgSyncLog record, @Param("example") OrgSyncLogExample example);

    int updateByExample(@Param("record") OrgSyncLog record, @Param("example") OrgSyncLogExample example);

    int updateByPrimaryKeySelective(OrgSyncLog record);

    int updateByPrimaryKey(OrgSyncLog record);

    int batchInsert(@Param("list") List<OrgSyncLog> list);

    int batchInsertSelective(@Param("list") List<OrgSyncLog> list, @Param("selective") OrgSyncLog.Column ... selective);
}
