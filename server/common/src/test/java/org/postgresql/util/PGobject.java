package org.postgresql.util;

/**
 * PostgreSQL 驱动 {@code PGobject} 的测试桩。
 *
 * <p>仅在 common 模块测试 classpath 中提供，用于验证 PostgreSQL 分支。
 * 该桩不会进入生产包。</p>
 */
public class PGobject {

    private String type;
    private String value;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
