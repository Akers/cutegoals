package com.cutegoals.taskreview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.task.TaskReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
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

    @Select("SELECT COUNT(r.id) FROM task_review r " +
            "JOIN task_attempt t ON r.attempt_id = t.id " +
            "JOIN task_assignment a ON t.assignment_id = a.id " +
            "WHERE a.child_id = #{childId} AND a.template_id = #{templateId} " +
            "AND r.decision = 'APPROVED'")
    long countApprovedByTemplateAndChild(@Param("childId") Long childId, @Param("templateId") Long templateId);

    @Select("<script>" +
            "SELECT a.template_id AS templateId, COUNT(r.id) AS approvedCount " +
            "FROM task_review r " +
            "JOIN task_attempt t ON r.attempt_id = t.id " +
            "JOIN task_assignment a ON t.assignment_id = a.id " +
            "WHERE a.child_id = #{childId} " +
            "AND r.decision = 'APPROVED' " +
            "AND a.template_id IN " +
            "<foreach collection='templateIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY a.template_id " +
            "</script>")
    List<Map<String, Object>> countApprovedBatch(@Param("childId") Long childId, @Param("templateIds") List<Long> templateIds);

    @Select("SELECT id FROM task_assignment " +
            "WHERE child_id = #{childId} AND template_id = #{templateId} " +
            "ORDER BY template_id LIMIT 1 FOR UPDATE")
    Long lockAssignmentByChildTemplate(@Param("childId") Long childId, @Param("templateId") Long templateId);
}
