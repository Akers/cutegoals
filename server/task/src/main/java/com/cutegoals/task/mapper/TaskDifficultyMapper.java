package com.cutegoals.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.task.TaskDifficulty;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TaskDifficultyMapper extends BaseMapper<TaskDifficulty> {

    @Select("SELECT * FROM task_difficulty WHERE template_id = #{templateId} ORDER BY display_order ASC")
    List<TaskDifficulty> findByTemplateId(@Param("templateId") Long templateId);

    @Select("SELECT * FROM task_difficulty WHERE template_id = #{templateId} AND enabled = true ORDER BY display_order ASC")
    List<TaskDifficulty> findEnabledByTemplateId(@Param("templateId") Long templateId);

    @Select("SELECT COUNT(*) FROM task_difficulty WHERE template_id = #{templateId} AND enabled = true")
    int countEnabledByTemplateId(@Param("templateId") Long templateId);
}
