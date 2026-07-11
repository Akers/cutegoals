package com.cutegoals.taskreview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.task.TaskReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface TaskReviewMapper extends BaseMapper<TaskReview> {

    @Select("SELECT * FROM task_review WHERE id = #{id}")
    Optional<TaskReview> findById(@Param("id") Long id);

    @Select("SELECT * FROM task_review WHERE attempt_id = #{attemptId}")
    Optional<TaskReview> findByAttemptId(@Param("attemptId") Long attemptId);

    @Select("SELECT * FROM task_review WHERE attempt_id = #{attemptId} FOR UPDATE")
    Optional<TaskReview> findByAttemptIdForUpdate(@Param("attemptId") Long attemptId);

    @Select("SELECT * FROM task_review WHERE idempotency_key = #{key}")
    Optional<TaskReview> findByIdempotencyKey(@Param("key") String key);

    @Select("SELECT * FROM task_review WHERE assignment_id = #{assignmentId} ORDER BY created_at DESC")
    java.util.List<TaskReview> findByAssignmentId(@Param("assignmentId") Long assignmentId);

    @Select("SELECT COUNT(*) FROM task_review WHERE attempt_id = #{attemptId}")
    int countByAttemptId(@Param("attemptId") Long attemptId);
}
