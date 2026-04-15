package com.anxin.travel.module.guard.mapper;

import com.anxin.travel.module.guard.entity.FamilyGuard;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface FamilyGuardMapper extends BaseMapper<FamilyGuard> {
    
    /**
     * 查询亲友已绑定的长辈数量
     */
    @Select("SELECT COUNT(*) FROM family_guard WHERE guardian_id = #{guardianId} AND status = 1")
    int countByGuardianId(@Param("guardianId") Long guardianId);
    
    /**
     * 查询长辈被多少人绑定（用于限制最多4人）
     */
    @Select("SELECT COUNT(*) FROM family_guard WHERE elder_id = #{elderId} AND status = 1")
    int countByElderId(@Param("elderId") Long elderId);
    
    /**
     * 查询长辈的所有活跃绑定亲友
     */
    @Select("SELECT * FROM family_guard WHERE elder_id = #{elderId} AND status = 1 ORDER BY bind_time DESC")
    List<FamilyGuard> selectActiveGuardsByElderId(@Param("elderId") Long elderId);
    
    /**
     * 批量激活待激活的绑定
     */
    @Update("UPDATE family_guard SET elder_id = #{elderId}, status = 1, activate_time = NOW() " +
            "WHERE elder_phone = #{elderPhone} AND status = 0")
    int batchActivate(@Param("elderId") Long elderId, @Param("elderPhone") String elderPhone);
    
    /**
     * 查询待激活的绑定记录
     */
    @Select("SELECT * FROM family_guard WHERE elder_phone = #{elderPhone} AND status = 0")
    List<FamilyGuard> selectPendingByElderPhone(@Param("elderPhone") String elderPhone);
    
    /**
     * 查询亲友与某长辈的绑定关系
     */
    @Select("SELECT * FROM family_guard WHERE guardian_id = #{guardianId} AND elder_id = #{elderId} AND status = 1")
    FamilyGuard selectByGuardianAndElder(@Param("guardianId") Long guardianId, @Param("elderId") Long elderId);
}
