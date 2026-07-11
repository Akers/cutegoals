package com.cutegoals.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.task.TaskAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface TaskAssignmentMapper extends BaseMapper<TaskAssignment> {

    @Select("SELECT * FROM task_assignment WHERE id = #{id}")
    Optional<TaskAssignment> findById(@Param("id") Long id);

    @Select("SELECT * FROM task_assignment WHERE idempotency_key = #{key} AND family_id = #{familyId}")
    Optional<TaskAssignment> findByIdempotencyKey(@Param("key") String key, @Param("familyId") Long familyId);

    @Select("SELECT * FROM task_assignment WHERE occurrence_key = #{key}")
    Optional<TaskAssignment> findByOccurrenceKey(@Param("key") String key);

    @Select("SELECT COUNT(*) FROM task_assignment WHERE occurrence_key = #{key}")
    int countByOccurrenceKey(@Param("key") String key);
}
