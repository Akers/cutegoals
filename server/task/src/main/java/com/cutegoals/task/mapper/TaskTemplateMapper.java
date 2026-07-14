package com.cutegoals.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.task.TaskTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TaskTemplateMapper extends BaseMapper<TaskTemplate> {

    @Select("SELECT * FROM task_template WHERE id = #{id}")
    Optional<TaskTemplate> findById(@Param("id") Long id);

    @Select("SELECT * FROM task_template WHERE id = #{id} AND deleted = false")
    Optional<TaskTemplate> findActiveById(@Param("id") Long id);

    @Select("SELECT MAX(version) FROM task_template WHERE id = #{id}")
    Integer getCurrentVersion(@Param("id") Long id);

    @Update("UPDATE task_template SET version = version + 1, updated_at = NOW() WHERE id = #{id} AND version = #{version}")
    int optimisticUpdate(@Param("id") Long id, @Param("version") Integer version);

    @Select("SELECT * FROM task_template WHERE task_type = 'REPEAT' AND enabled = true AND deleted = false")
    List<TaskTemplate> findEnabledRepeatTemplates();
}
