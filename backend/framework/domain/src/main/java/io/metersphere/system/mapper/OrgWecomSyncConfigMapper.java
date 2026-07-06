package io.metersphere.system.mapper;

import io.metersphere.system.domain.OrgWecomSyncConfig;
import io.metersphere.system.domain.OrgWecomSyncConfigExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface OrgWecomSyncConfigMapper {
    long countByExample(OrgWecomSyncConfigExample example);

    int deleteByExample(OrgWecomSyncConfigExample example);

    int deleteByPrimaryKey(String id);

    int insert(OrgWecomSyncConfig record);

    int insertSelective(OrgWecomSyncConfig record);

    List<OrgWecomSyncConfig> selectByExample(OrgWecomSyncConfigExample example);

    OrgWecomSyncConfig selectByPrimaryKey(String id);

    int updateByExampleSelective(@Param("record") OrgWecomSyncConfig record, @Param("example") OrgWecomSyncConfigExample example);

    int updateByExample(@Param("record") OrgWecomSyncConfig record, @Param("example") OrgWecomSyncConfigExample example);

    int updateByPrimaryKeySelective(OrgWecomSyncConfig record);

    int updateByPrimaryKey(OrgWecomSyncConfig record);

    int batchInsert(@Param("list") List<OrgWecomSyncConfig> list);

    int batchInsertSelective(@Param("list") List<OrgWecomSyncConfig> list, @Param("selective") OrgWecomSyncConfig.Column ... selective);
}
