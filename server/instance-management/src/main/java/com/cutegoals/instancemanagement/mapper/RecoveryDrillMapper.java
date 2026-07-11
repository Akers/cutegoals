package com.cutegoals.instancemanagement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.instance.RecoveryDrill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * MyBatis-Plus Mapper for recovery_drill table.
 */
@Mapper
public interface RecoveryDrillMapper extends BaseMapper<RecoveryDrill> {

    @Select("SELECT * FROM recovery_drill ORDER BY completed_at DESC LIMIT 1")
    Optional<RecoveryDrill> findLatest();
}
