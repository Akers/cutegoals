package com.cutegoals.points.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.points.PointsBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

@Mapper
public interface PointsBalanceMapper extends BaseMapper<PointsBalance> {

    @Select("SELECT * FROM points_balance WHERE id = #{id}")
    Optional<PointsBalance> findById(@Param("id") Long id);

    @Select("SELECT * FROM points_balance WHERE child_id = #{childId}")
    Optional<PointsBalance> findByChildId(@Param("childId") Long childId);

    @Select("SELECT * FROM points_balance WHERE child_id = #{childId} FOR UPDATE")
    Optional<PointsBalance> findByChildIdForUpdate(@Param("childId") Long childId);

    @Select("SELECT * FROM points_balance WHERE child_id = #{childId} AND version = #{version} FOR UPDATE")
    Optional<PointsBalance> findByChildIdAndVersionForUpdate(@Param("childId") Long childId, @Param("version") Integer version);

    @Update("UPDATE points_balance SET balance = #{balance}, total_earned = #{totalEarned}, version = version + 1 " +
            "WHERE child_id = #{childId} AND version = #{version}")
    int updateBalanceWithVersion(@Param("childId") Long childId, @Param("balance") Integer balance,
                                  @Param("totalEarned") Integer totalEarned, @Param("version") Integer version);

    @Update("UPDATE points_balance SET balance = #{balance}, version = version + 1 " +
            "WHERE child_id = #{childId} AND version = #{version}")
    int updateBalanceOnlyWithVersion(@Param("childId") Long childId, @Param("balance") Integer balance,
                                      @Param("version") Integer version);
}
