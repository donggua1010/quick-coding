package ${spkg};

import ${epkg}.${entity};
<#if key.needsImport>
import ${key.javaType};
</#if>
import java.util.List;

/**
 * ${entity} 业务服务接口
 * <p>定义 ${entity} 相关的查询、增删改业务方法（自动生成）。</p>
 */
public interface ${entity}Service {

    /**
     * 根据主键查询单条记录
     *
     * @param ${key.fieldName} 主键
     * @return 匹配的记录；不存在时返回 null
     */
    ${entity} selectByPrimaryKey(${key.javaType} ${key.fieldName});

    /**
     * 条件列表查询：非主键字段仅在非空时作为过滤条件
     *
     * @param record 查询条件
     * @return 满足条件的记录列表
     */
    List<${entity}> selectList(${entity} record);

    /**
     * 新增一条记录
     *
     * @param entity 待新增的记录
     * @return 受影响行数
     */
    int insert(${entity} entity);

    /**
     * 根据主键更新记录（仅更新非空字段）
     *
     * @param entity 待更新的记录
     * @return 受影响行数
     */
    int updateByPrimaryKey(${entity} entity);

    /**
     * 根据主键删除记录
     *
     * @param ${key.fieldName} 主键
     * @return 受影响行数
     */
    int deleteByPrimaryKey(${key.javaType} ${key.fieldName});
}