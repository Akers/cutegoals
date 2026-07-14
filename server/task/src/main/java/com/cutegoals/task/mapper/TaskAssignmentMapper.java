package com.cutegoals.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.task.TaskAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TaskAssignmentMapper extends BaseMapper<TaskAssignment> {

    @Select("SELECT * FROM task_assignment WHERE id = #{id}")
    Optional<TaskAssignment> findById(@Param("id") Long id);

    @Select("SELECT * FROM task_assignment WHERE id = #{id} FOR UPDATE")
    Optional<TaskAssignment> findByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM task_assignment WHERE idempotency_key = #{key} AND family_id = #{familyId}")
    Optional<TaskAssignment> findByIdempotencyKey(@Param("key") String key, @Param("familyId") Long familyId);

    @Select("SELECT * FROM task_assignment WHERE occurrence_key = #{key}")
    Optional<TaskAssignment> findByOccurrenceKey(@Param("key") String key);

    @Select("SELECT COUNT(*) FROM task_assignment WHERE occurrence_key = #{key}")
    int countByOccurrenceKey(@Param("key") String key);

    @Select("SELECT COUNT(*) FROM task_assignment WHERE difficulty_id = #{difficultyId}")
    int countByDifficultyId(@Param("difficultyId") Long difficultyId);

    @Update("UPDATE task_assignment SET cancelled = true, cancelled_at = NOW(), cancelled_by = #{cancelledBy}, "
            + "cancelled_reason = #{reason} "
            + "WHERE id = #{id} AND cancelled = false AND status != 'APPROVED'")
    int cancelWithCondition(@Param("id") Long id, @Param("cancelledBy") Long cancelledBy,
                            @Param("reason") String reason);

    // === REPEAT scheduler queries ===

    @Select("SELECT * FROM task_assignment WHERE template_id = #{templateId} AND status = 'OPEN' AND deadline < NOW()")
    List<TaskAssignment> findExpiredOpenByTemplate(@Param("templateId") Long templateId);

    @Select("SELECT * FROM task_assignment WHERE template_id = #{templateId} AND status = 'PENDING_OPEN'")
    List<TaskAssignment> findPendingOpenByTemplate(@Param("templateId") Long templateId);

    @Update("UPDATE task_assignment SET status = 'EXPIRED' WHERE id = #{id} AND status = 'OPEN'")
    int expireAssignment(@Param("id") Long id);

    @Update("UPDATE task_assignment SET status = 'OPEN' WHERE id = #{id} AND status = 'PENDING_OPEN'")
    int openAssignment(@Param("id") Long id);
}
