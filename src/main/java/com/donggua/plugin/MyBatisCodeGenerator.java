package com.donggua.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 根据 {@link CodeGenConfig} 渲染并生成 MyBatis 各层代码文件。
 *
 * <p>生成的产物通过 FreeMarker 模板（见 {@code /templates/*.ftl}）输出，模板引擎由
 * {@link TemplateRenderer} 提供（支持用户自定义模板组与内置默认模板）。
 * 本类负责：准备模板渲染上下文（model）→ 调用渲染 → 封装成 {@link OutputFile}。
 *
 * <p>每个文件都在单独的方法里构造 model 与模板名，保持职责单一：
 * <ul>
 *   <li>{@code entity/*.java} —— 实体类（Lombok）</li>
 *   <li>{@code mapper/*Mapper.java} —— Mapper 接口</li>
 *   <li>{@code mapper/*Mapper.xml} —— MyBatis 映射 XML</li>
 *   <li>{@code service/*Service.java} —— Service 接口</li>
 *   <li>{@code service/impl/*ServiceImpl.java} —— Service 实现</li>
 *   <li>{@code controller/*Controller.java} —— 控制器</li>
 * </ul>
 */
public final class MyBatisCodeGenerator {

    /**
     * 生成产物的元数据：目标目录类型（"CODE" 表示源代码目录，"RES" 表示资源目录）、
     * 相对于目录的路径以及文件内容文本。
     */
    public static final class OutputFile {
        /** 目标类型："CODE" 或 "RES"。 */
        public final String kind;
        /** 相对路径，例如 {@code entity/User.java}。 */
        public final String path;
        /** 文件内容（已渲染的文本）。 */
        public final String content;

        OutputFile(String kind, String path, String content) {
            this.kind = kind;
            this.path = path;
            this.content = content;
        }
    }

    /** 本次生成的配置。 */
    private final CodeGenConfig cfg;
    /** 实体类名（PascalCase），等价于 {@code cfg.entityName}。 */
    private final String entity;
    /** 依次累积生成的 OutputFile 列表。 */
    private final List<OutputFile> outputs = new ArrayList<>();

    /**
     * @param cfg 生成配置（由对话框构建）
     */
    public MyBatisCodeGenerator(CodeGenConfig cfg) {
        this.cfg = cfg;
        this.entity = cfg.entityName;
    }

    /**
     * 构建所有模板共用的基础上下文。
     *
     * <p>FreeMarker 模板通过 key 取值，所有模板都使用同一组基础 key 再各自补充字段。
     *
     * @return 基础 model（包名、实体名、各层包名、表名、开关等）
     */
    private Map<String, Object> baseModel() {
        Map<String, Object> model = new LinkedHashMap<>();
        String p = cfg.basePackage;
        model.put("pkg", p);                 // 基础包
        model.put("epkg", p + ".entity");    // entity 包
        model.put("mpkg", p + ".mapper");    // mapper 包
        model.put("spkg", p + ".service");   // service 包
        model.put("implpkg", p + ".service.impl"); // service 实现包
        model.put("cpkg", p + ".controller");// controller 包
        model.put("entity", entity);         // 实体名（类名）
        model.put("entityVar", cfg.entityVar());       // 小驼峰变量名
        model.put("mapperVar", cfg.mapperVar());       // mapper 变量名
        model.put("serviceVar", cfg.entityVar() + "Service"); // service 变量名
        model.put("tableName", cfg.qualifiedTableName());     // SQL 中的表名（可能带 schema）
        model.put("withSwagger", cfg.withSwagger);            // 是否生成 Swagger 注解
        model.put("mysql", cfg.isMysql);                      // 数据源是否为 MySQL（生成 ON DUPLICATE KEY UPDATE）
        model.put("postgres", cfg.isPostgres);                // 数据源是否为 PostgreSQL（生成 ON CONFLICT/类型映射）
        return model;
    }

    /**
     * 把一列的 {@link ColumnMeta} 展开为模板可直接使用的 Map。
     *
     * @param c 列元数据
     * @return 模板用的字段 map
     */
    private static Map<String, Object> columnModel(ColumnMeta c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("columnName", c.columnName);   // 数据库列名
        m.put("fieldName", c.fieldName);     // Java 字段名（小驼峰）
        m.put("javaType", c.javaType);       // Java 类型（可能全限定）
        m.put("jdbcType", c.jdbcType);       // JDBC 类型
        m.put("comment", c.comment);         // 列注释
        m.put("primary", c.primary);         // 是否主键
        m.put("needsImport", c.needsImport());// 是否需要 import（实体类里用）
        // 单条插入/更新的参数占位符
        m.put("param", "#{" + c.fieldName + "}");
        // 批量插入时每个对象属性的参数占位符（批量参数对象名为 record）
        m.put("batchParam", "#{record." + c.fieldName + "}");
        m.put("simpleType", c.simpleType()); // 简单类名（去包名）
        return m;
    }

    /**
     * 把主键列放入 model 的 "key" 字段。
     *
     * @param model 基础 model（就地修改）
     */
    private void putKey(Map<String, Object> model) {
        Map<String, Object> key = columnModel(cfg.keyColumn());
        model.put("key", key);
    }

    /**
     * 渲染一个模板并把结果与模板名结合。
     *
     * @param templateName 模板文件名（不含路径）
     * @param model        模板上下文
     * @return 渲染出的文本
     */
    private String render(String templateName, Map<String, Object> model) {
        return TemplateRenderer.render(templateName, model, cfg.templateGroup);
    }

    /**
     * 按配置生成全部选中的文件。
     *
     * <p>若配置了 service，会同时生成接口与实现；其余模块各自独立开关。
     *
     * @return 生成的 OutputFile 列表（内容尚未写盘，写盘由调用方完成）
     */
    public List<OutputFile> generate() {
        outputs.clear();
        if (cfg.includeEntity) {
            outputs.add(entityFile());
        }
        if (cfg.includeMapper) {
            outputs.add(mapperFile());
        }
        if (cfg.includeXml) {
            outputs.add(mapperXmlFile());
        }
        if (cfg.includeService) {
            outputs.add(serviceFile());
            outputs.add(serviceImplFile());
        }
        if (cfg.includeController) {
            outputs.add(controllerFile());
        }
        return outputs;
    }

    /** 生成实体类文件。 */
    private OutputFile entityFile() {
        Map<String, Object> model = baseModel();
        // 需要 import 的类集合：默认 lombok.Data，加上所有全限定列类型
        Set<String> imports = new LinkedHashSet<>();
        imports.add("lombok.Data");
        for (ColumnMeta c : cfg.columns) {
            if (c.needsImport()) {
                imports.add(c.javaType);
            }
        }
        // 开启 Swagger 时额外 import Swagger 注解
        if (cfg.withSwagger) {
            imports.add("io.swagger.annotations.ApiModel");
            imports.add("io.swagger.annotations.ApiModelProperty");
        }
        model.put("imports", imports);
        model.put("fields", fields());
        return new OutputFile("CODE", "entity/" + entity + ".java", render("entity.ftl", model));
    }

    /** 生成 Mapper 接口文件。 */
    private OutputFile mapperFile() {
        Map<String, Object> model = baseModel();
        putKey(model);
        return new OutputFile("CODE", "mapper/" + entity + "Mapper.java", render("mapper.ftl", model));
    }

    /** 生成 Mapper XML 映射文件。 */
    private OutputFile mapperXmlFile() {
        Map<String, Object> model = baseModel();
        putKey(model);
        model.put("fields", fields());
        return new OutputFile("RES", entity + "Mapper.xml", render("mapper.xml.ftl", model));
    }

    /** 生成 Service 接口文件。 */
    private OutputFile serviceFile() {
        Map<String, Object> model = baseModel();
        putKey(model);
        return new OutputFile("CODE", "service/" + entity + "Service.java", render("service.ftl", model));
    }

    /** 生成 Service 实现文件。 */
    private OutputFile serviceImplFile() {
        Map<String, Object> model = baseModel();
        putKey(model);
        return new OutputFile("CODE", "service/impl/" + entity + "ServiceImpl.java",
                render("serviceImpl.ftl", model));
    }

    /** 生成 Controller 文件。 */
    private OutputFile controllerFile() {
        Map<String, Object> model = baseModel();
        putKey(model);
        return new OutputFile("CODE", "controller/" + entity + "Controller.java",
                render("controller.ftl", model));
    }

    /**
     * 把全部列转换成模板字段 map 的列表。
     *
     * @return 供模板 {@code <#list>} 使用的字段列表
     */
    private List<Map<String, Object>> fields() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ColumnMeta c : cfg.columns) {
            list.add(columnModel(c));
        }
        return list;
    }
}