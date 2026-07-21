package com.cutegoals.points.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.points.PointsLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mapper
public interface PointsLedgerMapper extends BaseMapper<PointsLedger> {

    @Select("SELECT * FROM points_ledger WHERE id = #{id}")
    Optional<PointsLedger> findById(@Param("id") Long id);

    @Select("SELECT * FROM points_ledger WHERE child_id = #{childId} AND business_ref = #{businessRef}")
    Optional<PointsLedger> findByBusinessRef(@Param("childId") Long childId, @Param("businessRef") String businessRef);

    @Select("SELECT * FROM points_ledger WHERE child_id = #{childId} ORDER BY created_at DESC, id DESC")
    List<PointsLedger> findByChildId(@Param("childId") Long childId);

    @Select("SELECT * FROM points_ledger WHERE child_id = #{childId} AND type = #{type} AND created_at >= #{startTime} AND created_at < #{endTime} ORDER BY created_at DESC, id DESC")
    List<PointsLedger> findByChildIdAndTypeAndDateRange(
            @Param("childId") Long childId,
            @Param("type") String type,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("SELECT * FROM points_ledger WHERE child_id = #{childId} AND created_at >= #{startTime} AND created_at < #{endTime} ORDER BY created_at DESC, id DESC")
    List<PointsLedger> findByChildIdAndDateRange(
            @Param("childId") Long childId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("SELECT * FROM points_ledger WHERE child_id = #{childId} AND type = #{type} ORDER BY created_at DESC, id DESC")
    List<PointsLedger> findByChildIdAndType(
            @Param("childId") Long childId,
            @Param("type") String type);

    @Select("SELECT * FROM points_ledger WHERE child_id IN (${childIds}) AND created_at >= #{startTime} AND created_at < #{endTime} ORDER BY child_id, created_at DESC, id DESC")
    List<PointsLedger> findByChildIdsAndDateRange(
            @Param("childIds") String childIds,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("SELECT COALESCE(SUM(CASE WHEN type IN ('EARN','REFUND') THEN amount WHEN type = 'ADJUST' AND amount > 0 THEN amount ELSE 0 END), 0) " +
            "- COALESCE(SUM(CASE WHEN type = 'SPEND' THEN amount WHEN type = 'ADJUST' AND amount < 0 THEN -amount ELSE 0 END), 0) " +
            "FROM points_ledger WHERE child_id = #{childId} AND created_at < #{beforeTime}")
    int sumBalanceBefore(@Param("childId") Long childId, @Param("beforeTime") LocalDateTime beforeTime);

    @Select("SELECT COALESCE(SUM(CASE WHEN type = 'EARN' THEN amount ELSE 0 END), 0) " +
            "FROM points_ledger WHERE child_id = #{childId}")
    int sumTotalEarned(@Param("childId") Long childId);

    @Select("SELECT COALESCE(SUM(l.amount), 0) " +
            "FROM points_ledger l " +
            "JOIN task_attempt t ON l.business_ref = CONCAT('ATTEMPT_', t.id) " +
            "JOIN task_assignment a ON t.assignment_id = a.id " +
            "WHERE a.child_id = #{childId} " +
            "AND a.template_id = #{templateId} " +
            "AND l.type = 'EARN'")
    long sumEarnByTemplateAndChild(@Param("childId") Long childId, @Param("templateId") Long templateId);

    @Select("<script>" +
            "SELECT a.template_id AS templateId, COALESCE(SUM(l.amount), 0) AS earnedPoints " +
            "FROM points_ledger l " +
            "JOIN task_attempt t ON l.business_ref = CONCAT('ATTEMPT_', t.id) " +
            "JOIN task_assignment a ON t.assignment_id = a.id " +
            "WHERE a.child_id = #{childId} " +
            "AND l.type = 'EARN' " +
            "AND a.template_id IN " +
            "<foreach collection='templateIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY a.template_id " +
            "</script>")
    List<Map<String, Object>> sumEarnBatch(@Param("childId") Long childId, @Param("templateIds") List<Long> templateIds);
}
