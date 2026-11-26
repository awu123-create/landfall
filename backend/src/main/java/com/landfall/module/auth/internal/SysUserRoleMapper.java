package com.landfall.module.auth.internal;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface SysUserRoleMapper extends BaseMapper<SysUserRoleEntity> {

    @Select(
            """
            SELECT r.code AS role_code, p.code AS permission_code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            LEFT JOIN sys_role_permission rp ON rp.role_id = r.id
            LEFT JOIN sys_permission p ON p.id = rp.permission_id
            WHERE ur.user_id = #{userId}
            """)
    List<RolePermissionRow> selectRolePermissionsByUserId(@Param("userId") UUID userId);

    record RolePermissionRow(String roleCode, String permissionCode) {}
}
