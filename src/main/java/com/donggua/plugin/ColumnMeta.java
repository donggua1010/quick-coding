package com.donggua.plugin;

import com.intellij.database.psi.DbColumn;
import com.intellij.database.util.DasUtil;

import java.util.Locale;

/**
 * 数据库表列的 Java 侧元数据。
 *
 * <p>一个 {@code ColumnMeta} 封装了生成代码时所需的单列信息：
 * 数据库列名、对应的 Java 字段名（小驼峰）、Java 类型、MyBatis JDBC 类型、注释以及是否为
 * 主键。它同时承担“数据库类型 -&gt; Java 类型 / JDBC 类型”的映射职责（见内部类
 * {@link TypeMapping}）。
 *
 * <p>本类是不可变的值对象：所有字段都是 {@code final}，字段名/类型在构造时一次性计算好，
 * 后续模板渲染直接读取。
 */
public final class ColumnMeta {

    /** 数据库中的原始列名，例如 {@code user_id}。 */
    public final String columnName;
    /** 生成的 Java 字段名（小驼峰），例如 {@code userId}。 */
    public final String fieldName;
    /** Java 类型，可能是简单类型（{@code String}）或全限定类型（{@code java.util.Date}）。 */
    public final String javaType;
    /** 对应的 MyBatis JDBC 类型大写字面量，例如 {@code BIGINT}。 */
    public final String jdbcType;
    /** 数据库列的注释说明（可能为空字符串）。 */
    public final String comment;
    /** 该列是否为主键。 */
    public final boolean primary;

    /**
     * 从 IntelliJ 的数据库 PSI 列对象构建元数据。
     *
     * @param column  数据库列（{@link DbColumn}）
     * @param primary 该列是否为主键
     */
    public ColumnMeta(DbColumn column, boolean primary) {
        this.comment = column.getComment() == null ? "" : column.getComment();
        this.columnName = column.getName();
        // 数据库列名（snake_case）转为 Java 小驼峰字段名
        this.fieldName = Naming.toCamel(columnName);
        // 依据数据库类型推导 Java/JDBC 类型
        TypeMapping mapping = TypeMapping.from(column);
        this.javaType = mapping.javaType;
        this.jdbcType = mapping.jdbcType;
        this.primary = primary;
    }

    /**
     * 手工构建（主要用于单元测试或非 PSI 场景）。
     *
     * @param columnName 列名
     * @param fieldName  字段名
     * @param javaType   Java 类型
     * @param jdbcType   JDBC 类型
     * @param comment    注释
     */
    public ColumnMeta(String columnName, String fieldName, String javaType, String jdbcType, String comment) {
        this.columnName = columnName;
        this.fieldName = fieldName;
        this.javaType = javaType;
        this.jdbcType = jdbcType;
        this.comment = comment;
        this.primary = false;
    }

    /**
     * 判断该列的类型是否是“需要 import 的全限定类型”。
     *
     * <p>简单类型名字符串中不包含 {@code .}（如 {@code String}、{@code Integer}、
     * {@code Boolean}），位于 {@code java.lang} 包内无需显式 import；而像
     * {@code java.util.Date}、{@code java.math.BigDecimal} 这种全限定名需要 import。
     *
     * @return 若 Java 类型是全限定类型则返回 {@code true}
     */
    public boolean needsImport() {
        return javaType != null && javaType.contains(".");
    }

    /**
     * 返回 Java 类型的简单类名（去掉包名前缀）。
     *
     * @return 例如对 {@code java.util.Date} 返回 {@code Date}；无包名时原样返回
     */
    public String simpleType() {
        if (javaType == null) {
            return "";
        }
        int dot = javaType.lastIndexOf('.');
        return dot < 0 ? javaType : javaType.substring(dot + 1);
    }

    /**
     * 判断数据库列是否为主键，稳妥版：任何异常都视为“不是主键”。
     *
     * @param column 数据库列
     * @return 主键返回 {@code true}，否则（含异常）返回 {@code false}
     */
    public static boolean isPrimary(DbColumn column) {
        try {
            return DasUtil.isPrimary(column);
        } catch (Throwable t) {
            // 某些数据库方言/元数据可能不支持 isPrimary，失败时保守处理
            return false;
        }
    }

/**
     * 数据库类型映射：把数据库数据类型的字符串规范映射为 Java 类型和 JDBC 类型。
     *
     * <p>原理：把 {@link DbColumn} 的数据类型描述转小写后按关键字顺序做子串匹配。
     * 需要注意匹配顺序，例如 {@code bigint} 必须排在 {@code int} 之前，
     * 否则 {@code bigint} 会先被 {@code int} 分支匹配而误判为 Integer。
     */
    private static final class TypeMapping {
        /** 映射后的 Java 类型。 */
        final String javaType;
        /** MyBatis 的 JDBC 类型大写常量。 */
        final String jdbcType;

        TypeMapping(String javaType, String jdbcType) {
            this.javaType = javaType;
            this.jdbcType = jdbcType;
        }

        /**
         * 根据数据库列的数据类型字符串生成类型映射。
         *
         * @param column 数据库列
         * @return 包含 Java 类型与 JDBC 类型的映射对象
         */
        static TypeMapping from(DbColumn column) {
            // 取列数据类型的字符串描述（如 "BIGINT UNSIGNED"、"varchar(255)"、"decimal(10,2)"、"int8"）
            String spec = "";
            try {
                if (column.getDataType() != null) {
                    spec = column.getDataType().toString();
                }
            } catch (Throwable ignore) {
                // 取不到类型描述时按 String 处理
            }
            String s = spec.toLowerCase(Locale.ROOT);
            if (s.contains("bigint") || s.contains("bigserial") || s.contains("int8")) {
                // bigint / bigserial / int8 -> Long
                return new TypeMapping("Long", "BIGINT");
            } else if (s.contains("smallint") || s.contains("int2")) {
                return new TypeMapping("Integer", "SMALLINT");
            } else if (s.contains("tinyint")) {
                // tinyint 在 MySQL 常用于布尔/标志位，这里按小整数处理
                return new TypeMapping("Integer", "TINYINT");
            } else if (s.contains("smallserial") || s.contains("serial")) {
                // PostgreSQL 自增列 serial/smallserial -> Integer
                return new TypeMapping("Integer", "INTEGER");
            } else if (s.contains("decimal") || s.contains("numeric") || s.contains("number") || s.contains("money")) {
                // 精确小数/金额类型 -> BigDecimal
                return new TypeMapping("java.math.BigDecimal", "DECIMAL");
            } else if (s.contains("double") || s.contains("float8")) {
                // double precision / float8 -> Double
                return new TypeMapping("Double", "DOUBLE");
            } else if (s.contains("real") || s.contains("float4") || s.contains("float")) {
                return new TypeMapping("Float", "FLOAT");
            } else if (s.contains("bool") || s.contains("bit")) {
                // boolean / bit / bool 都映射为 Boolean
                return new TypeMapping("Boolean", "BOOLEAN");
            } else if (s.contains("char") || s.contains("text") || s.contains("clob")
                    || s.contains("uuid") || s.contains("json") || s.contains("xml")) {
                // 字符、文本、CLOB、uuid（PG）、json/jsonb、xml 一律映射为 String
                return new TypeMapping("String", "VARCHAR");
            } else if (s.contains("bytea") || s.contains("blob") || s.contains("binary")) {
                // PostgreSQL bytea / MySQL blob / binary -> 字节数组
                return new TypeMapping("byte[]", "BINARY");
            } else if (s.contains("date") || s.contains("time") || s.contains("timestamp") || s.contains("datetime")) {
                // 时间/日期类型 -> java.util.Date（全限定，便于统一）
                return new TypeMapping("java.util.Date", "TIMESTAMP");
            } else if (s.contains("int")) {
                // int / mediumint / int4 等 -> Integer
                return new TypeMapping("Integer", "INTEGER");
            }
            // 未知类型兜底：全部按字符串处理
            return new TypeMapping("String", "VARCHAR");
        }
    }
}