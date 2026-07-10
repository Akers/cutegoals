package com.cutegoals.family.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cutegoals.common.entity.family.DeviceBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus Mapper for device_binding table.
 */
@Mapper
public interface DeviceBindingMapper extends BaseMapper<DeviceBinding> {

    @Select("SELECT * FROM device_binding WHERE id = #{id}")
    Optional<DeviceBinding> findById(@Param("id") Long id);

    @Select("SELECT * FROM device_binding WHERE device_id = #{deviceId} AND status = 'ACTIVE'")
    Optional<DeviceBinding> findActiveByDeviceId(@Param("deviceId") String deviceId);

    @Select("SELECT * FROM device_binding WHERE credential_hash = #{credentialHash} AND status = 'ACTIVE'")
    Optional<DeviceBinding> findByCredentialHash(@Param("credentialHash") String credentialHash);

    @Select("SELECT * FROM device_binding WHERE family_id = #{familyId} AND status = 'ACTIVE'")
    List<DeviceBinding> findActiveByFamilyId(@Param("familyId") Long familyId);

    @Update("UPDATE device_binding SET status = 'REVOKED' WHERE id = #{id}")
    int revokeById(@Param("id") Long id);

    @Update("UPDATE device_binding SET status = 'REVOKED' WHERE device_id = #{deviceId} AND status = 'ACTIVE'")
    int revokeByDeviceId(@Param("deviceId") String deviceId);
}
