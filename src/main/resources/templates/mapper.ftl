package ${mpkg};

import ${epkg}.${entity};
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
<#if key.needsImport>
import ${key.javaType};
</#if>
import java.util.List;

/**
 * ${entity} 数据访问接口（Mapper）
 * <p>对应 MyBatis 映射文件：${entity}Mapper.xml</p>
 * <p>通过数据库工具自动生成。</p>
 */
@Mapper
public interface ${entity}Mapper {

    /**
     * 根据主键查询单条记录
     *
     * @param ${key.fieldName} 主键（${key.columnName}）
     * @return 匹配的记录；不存在时返回 null
     */
    ${entity} selectByPrimaryKey(${key.javaType} ${key.fieldName});

    /**
     * 条件列表查询：非主键字段仅在非空时作为过滤条件
     *
     * @param record 查询条件（仅填充需要过滤的字段）
     * @return 满足条件的记录列表
     */
    List<${entity}> selectList(${entity} record);

    /**
     * 插入一条新记录
     *
     * @param record 待插入的记录
     * @return 受影响行数
     */
    int insert(${entity} record);

    /**
     * 根据主键更新记录（仅更新非空字段）
     *
     * @param record 待更新的记录（须包含主键）
     * @return 受影响行数
     */
    int updateByPrimaryKey(${entity} record);

    /**
     * 根据主键删除记录
     *
     * @param ${key.fieldName} 主键（${key.columnName}）
     * @return 受影响行数
     */
    int deleteByPrimaryKey(${key.javaType} ${key.fieldName});

    /**
     * 插入或更新（冲突时转为更新：MySQL 用 ON DUPLICATE KEY UPDATE / PostgreSQL 用 ON CONFLICT）
     *
     * @param record 待插入或更新的记录
     * @return 受影响行数
     */
    int insertOrUpdate(${entity} record);

    /**
     * 批量插入或更新（依赖数据库唯一键冲突时转为更新）
     *
     * @param records 记录集合
     * @return 受影响行数
     */
    int batchInsertOrUpdate(@Param("list") List<${entity}> records);
}