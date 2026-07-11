package com.cutegoals.taskreview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.task.TaskAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface TaskAttemptMapper extends BaseMapper<TaskAttempt> {

    @Select("SELECT * FROM task_attempt WHERE id = #{id}")
    Optional<TaskAttempt> findById(@Param("id") Long id);

    @Select("SELECT * FROM task_attempt WHERE id = #{id} FOR UPDATE")
    Optional<TaskAttempt> findByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM task_attempt WHERE child_id = #{childId} AND idempotency_key = #{key}")
    Optional<TaskAttempt> findByIdempotencyKey(@Param("childId") Long childId, @Param("key") String key);

    @Select("SELECT COALESCE(MAX(attempt_number), 0) FROM task_attempt WHERE assignment_id = #{assignmentId} AND child_id = #{childId}")
    int getMaxAttemptNumber(@Param("assignmentId") Long assignmentId, @Param("childId") Long childId);

    @Select("SELECT * FROM task_attempt WHERE assignment_id = #{assignmentId} ORDER BY attempt_number ASC")
    java.util.List<TaskAttempt> findByAssignmentId(@Param("assignmentId") Long assignmentId);
}
