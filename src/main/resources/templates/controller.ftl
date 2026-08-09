package ${cpkg};

import ${epkg}.${entity};
import ${spkg}.${entity}Service;
<#if key.needsImport>
import ${key.javaType};
</#if>
<#if withSwagger>
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
</#if>
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

/**
 * ${entity} 控制器
 * <p>提供 ${entity} 的 REST 接口（自动生成）。</p>
 */
@RestController
<#if withSwagger>
@Api(tags = "${entityVar}管理")
</#if>
@RequestMapping("/${entityVar}")
public class ${entity}Controller {

    /** 业务服务 */
    @Autowired
    private ${entity}Service ${serviceVar};

    /**
     * 条件列表查询
     *
     * @param record 查询条件（仅填充需要过滤的字段）
     * @return 满足条件的记录列表
     */
    @GetMapping("/list")
    <#if withSwagger>
    @ApiOperation("列表查询")
    </#if>
    public List<${entity}> list(${entity} record) {
        return ${serviceVar}.selectList(record);
    }

    /**
     * 根据主键查询
     *
     * @param ${key.fieldName} 主键
     * @return 匹配的记录
     */
    @GetMapping("/{${key.fieldName}}")
    <#if withSwagger>
    @ApiOperation("根据主键查询")
    </#if>
    public ${entity} get(@PathVariable("${key.fieldName}") ${key.javaType} ${key.fieldName}) {
        return ${serviceVar}.selectByPrimaryKey(${key.fieldName});
    }

    /**
     * 新增记录
     *
     * @param entity 待新增的记录（JSON 请求体）
     * @return 受影响行数
     */
    @PostMapping
    <#if withSwagger>
    @ApiOperation("新增")
    </#if>
    public int add(@RequestBody ${entity} entity) {
        return ${serviceVar}.insert(entity);
    }

    /**
     * 更新记录（须包含主键）
     *
     * @param entity 待更新的记录（JSON 请求体）
     * @return 受影响行数
     */
    @PutMapping
    <#if withSwagger>
    @ApiOperation("更新")
    </#if>
    public int update(@RequestBody ${entity} entity) {
        return ${serviceVar}.updateByPrimaryKey(entity);
    }

    /**
     * 根据主键删除
     *
     * @param ${key.fieldName} 主键
     * @return 受影响行数
     */
    @DeleteMapping("/{${key.fieldName}}")
    <#if withSwagger>
    @ApiOperation("删除")
    </#if>
    public int remove(@PathVariable("${key.fieldName}") ${key.javaType} ${key.fieldName}) {
        return ${serviceVar}.deleteByPrimaryKey(${key.fieldName});
    }
}