package com.cutegoals.exchange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.exchange.ExchangeSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface ExchangeSnapshotMapper extends BaseMapper<ExchangeSnapshot> {

    @Select("SELECT * FROM exchange_snapshot WHERE exchange_id = #{exchangeId}")
    Optional<ExchangeSnapshot> findByExchangeId(@Param("exchangeId") Long exchangeId);
}
