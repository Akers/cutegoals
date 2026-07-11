package com.cutegoals.exchange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.exchange.Exchange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface ExchangeMapper extends BaseMapper<Exchange> {

    @Select("SELECT * FROM exchange WHERE id = #{id}")
    Optional<Exchange> findById(@Param("id") Long id);

    @Select("SELECT * FROM exchange WHERE id = #{id} AND family_id = #{familyId}")
    Optional<Exchange> findByIdAndFamily(@Param("id") Long id, @Param("familyId") Long familyId);

    @Select("SELECT * FROM exchange WHERE id = #{id} AND family_id = #{familyId} FOR UPDATE")
    Optional<Exchange> findByIdAndFamilyForUpdate(@Param("id") Long id, @Param("familyId") Long familyId);

    @Select("SELECT * FROM exchange WHERE child_id = #{childId} AND idempotency_key = #{idempotencyKey}")
    Optional<Exchange> findByChildIdAndKey(@Param("childId") Long childId, @Param("idempotencyKey") String idempotencyKey);

    @Select("SELECT COUNT(1) FROM exchange WHERE prize_id = #{prizeId}")
    int countByPrizeId(@Param("prizeId") Long prizeId);

    @Select("SELECT COUNT(1) FROM exchange WHERE result_prize_id = #{prizeId}")
    int countByResultPrizeId(@Param("prizeId") Long prizeId);

    @Update("UPDATE exchange SET status = #{status}, fulfilled_at = #{fulfilledAt}, fulfilled_by = #{fulfilledBy} " +
            "WHERE id = #{id} AND status = 'PENDING_FULFILLMENT'")
    int fulfillExchange(@Param("id") Long id, @Param("status") String status,
                        @Param("fulfilledAt") LocalDateTime fulfilledAt,
                        @Param("fulfilledBy") Long fulfilledBy);

    @Update("UPDATE exchange SET status = #{status}, cancelled_at = #{cancelledAt}, cancelled_by = #{cancelledBy} " +
            "WHERE id = #{id} AND status = 'PENDING_FULFILLMENT'")
    int cancelExchange(@Param("id") Long id, @Param("status") String status,
                       @Param("cancelledAt") LocalDateTime cancelledAt,
                       @Param("cancelledBy") Long cancelledBy);
}
