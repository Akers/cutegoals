package com.cutegoals.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.task.TaskRecurrenceRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface TaskRecurrenceRuleMapper extends BaseMapper<TaskRecurrenceRule> {

    @Select("SELECT * FROM task_recurrence_rule WHERE template_id = #{templateId}")
    Optional<TaskRecurrenceRule> findByTemplateId(@Param("templateId") Long templateId);

    @Select("DELETE FROM task_recurrence_rule WHERE template_id = #{templateId}")
    void deleteByTemplateId(@Param("templateId") Long templateId);
}
