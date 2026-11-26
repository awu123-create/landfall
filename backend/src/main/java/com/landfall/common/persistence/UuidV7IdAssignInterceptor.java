package com.landfall.common.persistence;

import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.landfall.common.id.UuidV7Generator;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

/**
 * 插入时为 {@code UUID} 类型 {@code @TableId} 主键兜底分配单调 UUIDv7，保留调用方已显式赋值的 ID。
 */
@Intercepts({
    @Signature(
            type = Executor.class,
            method = "update",
            args = {MappedStatement.class, Object.class})
})
class UuidV7IdAssignInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        if (mappedStatement.getSqlCommandType() == SqlCommandType.INSERT) {
            Object entity = resolveEntity(invocation.getArgs()[1]);
            if (entity != null) {
                TableInfo tableInfo = TableInfoHelper.getTableInfo(entity.getClass());
                if (tableInfo != null
                        && UUID.class.equals(tableInfo.getKeyType())
                        && tableInfo.getPropertyValue(entity, tableInfo.getKeyProperty()) == null) {
                    tableInfo.setPropertyValue(entity, tableInfo.getKeyProperty(), UuidV7Generator.generate());
                }
            }
        }
        return invocation.proceed();
    }

    private static Object resolveEntity(Object parameter) {
        if (parameter instanceof Map<?, ?> map) {
            return map.get(Constants.ENTITY);
        }
        return parameter;
    }
}
