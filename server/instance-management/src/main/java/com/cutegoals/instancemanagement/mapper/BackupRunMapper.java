package com.cutegoals.instancemanagement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.instance.BackupRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * MyBatis-Plus Mapper for backup_run table.
 */
@Mapper
public interface BackupRunMapper extends BaseMapper<BackupRun> {

    @Select("SELECT * FROM backup_run ORDER BY started_at DESC LIMIT 1")
    Optional<BackupRun> findLatest();
}
