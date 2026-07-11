package com.cutegoals.instancemanagement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.instance.InstanceConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/**
 * MyBatis-Plus Mapper for instance_config table.
 */
@Mapper
public interface InstanceConfigMapper extends BaseMapper<InstanceConfig> {

    @Select("SELECT * FROM instance_config WHERE config_key = #{configKey}")
    Optional<InstanceConfig> findByKey(@Param("configKey") String configKey);

    @Update("UPDATE instance_config SET config_value = #{configValue}, masked = #{masked} WHERE config_key = #{configKey}")
    int updateByKey(@Param("configKey") String configKey, @Param("configValue") String configValue, @Param("masked") Boolean masked);
}
