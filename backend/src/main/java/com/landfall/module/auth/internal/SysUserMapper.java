package com.landfall.module.auth.internal;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface SysUserMapper extends BaseMapper<SysUserEntity> {

    default SysUserEntity selectActiveById(UUID id) {
        return selectOne(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getId, id)
                .isNull(SysUserEntity::getDeletedAt));
    }

    @Select(
            """
            SELECT id, username, email, password_hash, nickname, avatar_url, bio, status, created_at, updated_at, deleted_at
            FROM sys_user
            WHERE deleted_at IS NULL
              AND (LOWER(username) = LOWER(#{username}) OR LOWER(email) = LOWER(#{email}))
            """)
    List<SysUserEntity> selectActiveByUsernameOrEmail(@Param("username") String username, @Param("email") String email);
}
