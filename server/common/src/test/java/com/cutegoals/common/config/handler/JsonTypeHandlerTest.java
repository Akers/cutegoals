package com.cutegoals.common.config.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JsonTypeHandlerTest {

    @Mock
    PreparedStatement ps;

    @Mock
    Connection connection;

    @Mock
    DatabaseMetaData metaData;

    @Mock
    ResultSet resultSet;

    @Mock
    CallableStatement callableStatement;

    JsonTypeHandler handler = new JsonTypeHandler();

    @Test
    void setNonNullParameter_onH2Database_usesSetString() throws SQLException {
        when(ps.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("H2");

        handler.setParameter(ps, 1, "{}", null);

        verify(ps).setString(1, "{}");
        verify(ps, never()).setObject(anyInt(), any());
    }

    @Test
    void setNonNullParameter_onPostgreSQL_usesPGobject() throws SQLException {
        when(ps.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        String json = "{\"frequency\":\"WEEKLY\"}";
        handler.setParameter(ps, 2, json, null);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(ps).setObject(eq(2), captor.capture());
        Object value = captor.getValue();
        assertNotNull(value);
        assertEquals("org.postgresql.util.PGobject", value.getClass().getName());
        assertEquals("json", invoke(value, "getType"));
        assertEquals(json, invoke(value, "getValue"));
        verify(ps, never()).setString(anyInt(), anyString());
    }

    @Test
    void setNonNullParameter_onNullValue_usesSetNull() throws SQLException {
        handler.setParameter(ps, 1, null, JdbcType.OTHER);
        verify(ps).setNull(1, JdbcType.OTHER.TYPE_CODE);
    }

    @Test
    void getNullableResult_fromResultSetByName_returnsString() throws SQLException {
        when(resultSet.getString("type_config")).thenReturn("{\"a\":1}");
        assertEquals("{\"a\":1}", handler.getResult(resultSet, "type_config"));
    }

    @Test
    void getNullableResult_fromResultSetByIndex_returnsString() throws SQLException {
        when(resultSet.getString(3)).thenReturn("{\"b\":2}");
        assertEquals("{\"b\":2}", handler.getResult(resultSet, 3));
    }

    @Test
    void getNullableResult_fromCallableStatement_returnsString() throws SQLException {
        when(callableStatement.getString(4)).thenReturn("{\"c\":3}");
        assertEquals("{\"c\":3}", handler.getResult(callableStatement, 4));
    }

    private Object invoke(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (Exception e) {
            fail("Failed to invoke " + methodName + " on " + target, e);
            return null;
        }
    }
}
