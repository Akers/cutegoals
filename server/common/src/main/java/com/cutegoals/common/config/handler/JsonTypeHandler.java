package com.cutegoals.common.config.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JSON 字符串类型处理器。
 *
 * <p>字段类型保持为 {@link String}，写库时直接以原始字符串作为 JSON 内容。
 * PostgreSQL 的 json 列不接受 {@link PreparedStatement#setString(int, String)} 的参数类型，
 * 因此针对 PostgreSQL 使用 {@code org.postgresql.util.PGobject} 包装为 json 对象。
 * 其他数据库（如 H2）则回退到 setString。</p>
 *
 * <p>common 模块不直接依赖 postgresql 驱动，故通过反射创建 PGobject。</p>
 */
public class JsonTypeHandler extends BaseTypeHandler<String> {

    private static final String POSTGRESQL_PRODUCT_NAME = "PostgreSQL";
    private static final String PG_OBJECT_CLASS = "org.postgresql.util.PGobject";
    private static final String JSON_TYPE = "json";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        if (isPostgreSQL(ps.getConnection())) {
            setPostgreSQLJsonParameter(ps, i, parameter);
        } else {
            ps.setString(i, parameter);
        }
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }

    private boolean isPostgreSQL(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        return metaData != null && POSTGRESQL_PRODUCT_NAME.equalsIgnoreCase(metaData.getDatabaseProductName());
    }

    private void setPostgreSQLJsonParameter(PreparedStatement ps, int index, String value) throws SQLException {
        try {
            Class<?> pgObjectClass = Class.forName(PG_OBJECT_CLASS);
            Object pgObject = pgObjectClass.getDeclaredConstructor().newInstance();
            setProperty(pgObject, "setType", String.class, JSON_TYPE);
            setProperty(pgObject, "setValue", String.class, value);
            ps.setObject(index, pgObject);
        } catch (ClassNotFoundException e) {
            // PostgreSQL 驱动未在 classpath 中，回退到 setString
            ps.setString(index, value);
        } catch (Exception e) {
            throw new SQLException("Failed to set PostgreSQL JSON parameter at index " + index, e);
        }
    }

    private void setProperty(Object target, String methodName, Class<?> argType, Object value) throws Exception {
        Method method = target.getClass().getMethod(methodName, argType);
        method.invoke(target, value);
    }
}
