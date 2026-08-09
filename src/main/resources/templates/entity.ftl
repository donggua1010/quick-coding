package ${epkg};

<#list imports as i>
import ${i};
</#list>

/**
 * ${entity} 实体类
 * <p>对应数据库表：${tableName}</p>
 * <p>通过数据库工具自动生成，请勿手工修改字段结构。</p>
 */
@Data
<#if withSwagger>
@ApiModel(value = "${entity}", description = "${entity}实体")
</#if>
public class ${entity} {

<#list fields as f>
    /**
     * ${f.comment}
     * <p>数据库列：${f.columnName}（JDBC 类型：${f.jdbcType}）</p>
     */
    <#if withSwagger>
    @ApiModelProperty(value = "${f.comment}")
    </#if>
    private ${f.simpleType} ${f.fieldName};
</#list>
}