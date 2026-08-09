package com.donggua.plugin;

import com.intellij.database.psi.DbTable;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次代码生成任务的完整配置（不可变）。
 *
 * <p>由 {@link GenerateCodeDialog} 收集用户的全部选择后构造，之后交给
 * {@link MyBatisCodeGenerator} 使用。它既包含目标目录、包名、表名等基本信息，
 * 也包含是否生成 entity/mapper/xml/service/controller、是否生成 Swagger 注解、
 * SQL 是否携带 schema 前缀、生成后是否加入版本控制等开关。
 */
public final class CodeGenConfig {

    /** Java 包基础包名，例如 {@code com.example}. */
    public final String basePackage;
    /** 实体类名（PascalCase），例如 {@code User}. */
    public final String entityName;
    /** 数据库表名，例如 {@code t_user}. */
    public final String tableName;
    /** 表所属的 schema 名（可能为空字符串）。 */
    public final String schemaName;
    /** 代码文件输出目录（entity/mapper/service/controller 所在的基础目录）。 */
    public final String codeDir;
    /** 资源文件输出目录（mapper XML 所在目录）。 */
    public final String resDir;
    /** 该表所有列的 {@link ColumnMeta} 列表，按数据库中列的顺序排列。 */
    public final List<ColumnMeta> columns;

    /** 是否生成 entity 实体类。 */
    public final boolean includeEntity;
    /** 是否生成 Mapper 接口。 */
    public final boolean includeMapper;
    /** 是否生成 Mapper XML 映射文件。 */
    public final boolean includeXml;
    /** 是否生成 Service 接口。 */
    public final boolean includeService;
    /** 是否生成 Controller 控制器。 */
    public final boolean includeController;
    /** 是否在生成的代码中附加 Swagger 注解。 */
    public final boolean withSwagger;
    /** SQL 语句中是否携带 schema 前缀（例如 dbo.t_user）。 */
    public final boolean sqlWithSchema;
    /** 生成完成后是否调用 IDE 的版本控制功能把新文件加入 VCS。 */
    public final boolean addToVcs;
    /** 使用的模板组名称（默认组见 {@link TemplateSettings#DEFAULT_GROUP}）。 */
    public final String templateGroup;
    /** 数据源是否为 MySQL/MariaDB（影响 insertOrUpdate 的 ON DUPLICATE KEY UPDATE 语法）。 */
    public final boolean isMysql;
    /** 数据源是否为 PostgreSQL（影响 insertOrUpdate 的 ON CONFLICT 语法与类型映射）。 */
    public final boolean isPostgres;

    /**
     * 构造完整的生成配置。表名、schema、列元数据都会在构造时从
     * {@link DbTable} 中解析出来并固化。
     *
     * @param basePackage       基础包名
     * @param entityName        实体名（会强制转成 PascalCase）
     * @param table             数据库表
     * @param codeDir           代码输出目录
     * @param resDir            资源输出目录
     * @param templateGroup     模板组
     * @param includeEntity     是否生成 entity
     * @param includeMapper     是否生成 mapper
     * @param includeXml        是否生成 mapper XML
     * @param includeService    是否生成 service
     * @param includeController 是否生成 controller
     * @param withSwagger       是否生成 Swagger 注解
     * @param sqlWithSchema     SQL 是否包含 schema
     * @param addToVcs          生成后是否加入版本控制
     */
    public CodeGenConfig(String basePackage, String entityName, DbTable table,
                         String codeDir, String resDir, String templateGroup,
                         boolean includeEntity, boolean includeMapper, boolean includeXml,
                         boolean includeService, boolean includeController,
                         boolean withSwagger, boolean sqlWithSchema, boolean addToVcs) {
        this.basePackage = basePackage.trim();
        // 实体名统一转成 PascalCase，无论用户输入 "user" 还是 "User" 都输出类似 "User"
        this.entityName = Naming.toPascal(entityName);
        this.tableName = table.getName();
        this.schemaName = schemaOf(table);
        this.codeDir = codeDir;
        this.resDir = resDir;
        this.templateGroup = templateGroup;
        this.includeEntity = includeEntity;
        this.includeMapper = includeMapper;
        this.includeXml = includeXml;
        this.includeService = includeService;
        this.includeController = includeController;
        this.withSwagger = withSwagger;
        this.sqlWithSchema = sqlWithSchema;
        this.addToVcs = addToVcs;

        // 从数据源头探测数据库方言（MySQL / PostgreSQL 等），用于 SQL 语法与类型映射的适配
        boolean mysql = false, postgres = false;
        try {
            com.intellij.database.Dbms dbms = table.getDataSource().getDbms();
            if (dbms != null) {
                mysql = dbms.isMysql();
                postgres = dbms.isPostgres();
            }
        } catch (Throwable t) {
            // 拿不到 Dbms 时按通用处理
        }
        this.isMysql = mysql;
        this.isPostgres = postgres;

        // 提取表中所有列，转换为自定义的列元数据（非 DbColumn 的实现会被忽略）
        this.columns = new ArrayList<>();
        for (com.intellij.database.model.DasColumn dc : com.intellij.database.util.DasUtil.getColumns(table)) {
            if (dc instanceof com.intellij.database.psi.DbColumn c) {
                this.columns.add(new ColumnMeta(c, ColumnMeta.isPrimary(c)));
            }
        }
    }

    /**
     * 实体名转小驼峰（用于包/变量名），例如 {@code User} -&gt; {@code user}.
     */
    public String entityVar() {
        return Naming.toCamel(entityName);
    }

    /**
     * Mapper 接口（及 XML 中对应的变量名），例如 {@code userMapper}。
     *
     * <p>注意这里直接用“首字母小写 + Mapper”拼接而不是调用
     * {@code toCamel(entityName + "Mapper")}，因为后者实际上会得到一样的
     * {@code userMapper}，但这里显式实现更直观。
     */
    public String mapperVar() {
        return Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1) + "Mapper";
    }

    /**
     * 返回主键列；若表没有定义主键，则回退到第一列。
     *
     * <p>生成的数据如下 {@code key} 字段 / {@code where "id=..."} 等都会使用主键。
     *
     * @return 主键列，或第一列（当没有主键时）
     */
    public ColumnMeta keyColumn() {
        for (ColumnMeta c : columns) {
            if (c.primary) {
                return c;
            }
        }
        // 没有主键的表：取第一列作为默认主键
        return columns.get(0);
    }

    /**
     * 判断该表是否至少有一列（用于空表等异常情形的快速检查）。
     */
    public boolean hasColumns() {
        return !columns.isEmpty();
    }

    /**
     * 返回 SQL 里使用的表名，可能带 schema 前缀。
     *
     * @return 例如 {@code dbo.t_user}（开启了 {@link #sqlWithSchema} 且有 schema），
     *         否则纯表名 {@code t_user}
     */
    public String qualifiedTableName() {
        if (sqlWithSchema && schemaName != null && !schemaName.isEmpty()) {
            return schemaName + "." + tableName;
        }
        return tableName;
    }

    /**
     * 从 {@link DbTable} 提取 schema 名，任何异常都返回空串。
     *
     * <p>使用 {@link com.intellij.database.util.DasUtil#getSchema(DasObject)}
     * 读取元数据 schema。部分数据库可能抛异常，统一在这里兜底。
     *
     * @param table 数据库表
     * @return schema 名，或空串
     */
    private static String schemaOf(DbTable table) {
        try {
            String s = com.intellij.database.util.DasUtil.getSchema(table.getDasObject());
            return s == null ? "" : s;
        } catch (Throwable t) {
            return "";
        }
    }
}