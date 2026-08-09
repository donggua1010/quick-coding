<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<!-- ${entity} 的 MyBatis 映射文件（自动生成） -->
<mapper namespace="${mpkg}.${entity}Mapper">

    <!-- 实体与数据库列的映射关系 -->
    <resultMap id="BaseResultMap" type="${epkg}.${entity}">
<#list fields as f>
<#if f.primary>
        <!-- 主键列：${f.columnName} -->
        <id column="${f.columnName}" property="${f.fieldName}" jdbcType="${f.jdbcType}"/>
<#else>
        <!-- 普通列：${f.columnName} -->
        <result column="${f.columnName}" property="${f.fieldName}" jdbcType="${f.jdbcType}"/>
</#if>
</#list>
    </resultMap>

    <!-- 通用查询字段列表 -->
    <sql id="Base_Column_List">
        <#list fields as f>${f.columnName}<#sep>, </#sep></#list>
    </sql>

    <!-- 根据主键查询单条记录 -->
    <select id="selectByPrimaryKey" resultMap="BaseResultMap">
        select <include refid="Base_Column_List"/>
        from ${tableName}
        where ${key.columnName} = ${key.param}
    </select>

    <!-- 条件查询：非空字段作为过滤条件（自动拼接 WHERE） -->
    <select id="selectList" resultMap="BaseResultMap">
        select <include refid="Base_Column_List"/>
        from ${tableName}
        <where>
<#list fields as f>
<#if !f.primary>
            <if test="${f.fieldName} != null">
                and ${f.columnName} = ${f.param}
            </if>
</#if>
</#list>
        </where>
    </select>

    <!-- 插入一条记录 -->
    <insert id="insert" parameterType="${epkg}.${entity}">
        insert into ${tableName}
        (
<#list fields as f>            ${f.columnName}<#sep>,</#sep>
</#list>        )
        values
        (
<#list fields as f>            ${f.param}<#sep>,</#sep>
</#list>        )
    </insert>

    <!-- 根据主键更新（仅更新非空字段） -->
    <update id="updateByPrimaryKey" parameterType="${epkg}.${entity}">
        update ${tableName}
        <set>
<#list fields as f>
<#if !f.primary>
            <if test="${f.fieldName} != null">
                ${f.columnName} = ${f.param},
            </if>
</#if>
</#list>
        </set>
        where ${key.columnName} = ${key.param}
    </update>

    <!-- 插入或更新单条（MySQL 用 ON DUPLICATE KEY UPDATE，PostgreSQL 用 ON CONFLICT） -->
    <insert id="insertOrUpdate" parameterType="${epkg}.${entity}">
        insert into ${tableName}
        (
<#list fields as f>            ${f.columnName}<#sep>,</#sep>
</#list>        )
        values
        (
<#list fields as f>            ${f.param}<#sep>,</#sep>
</#list>        )
<#if postgres!false>
        on conflict (${key.columnName}) do update set
<#list fields as f>
<#if !f.primary>
            ${f.columnName} = excluded.${f.columnName}<#sep>,</#sep>
</#if>
</#list>
<#else>
        on duplicate key update
<#list fields as f>
<#if !f.primary>
        ${f.columnName} = values(${f.columnName})<#sep>,</#sep>
</#if>
</#list>
</#if>
    </insert>

    <!-- 批量插入或更新（MySQL 用 ON DUPLICATE KEY UPDATE，PostgreSQL 用 ON CONFLICT） -->
    <insert id="batchInsertOrUpdate">
        insert into ${tableName}
        (
<#list fields as f>            ${f.columnName}<#sep>,</#sep>
</#list>        )
        values
        <foreach collection="list" item="record" separator=",">
            (
<#list fields as f>                ${f.batchParam}<#sep>,</#sep>
</#list>            )
        </foreach>
<#if postgres!false>
        on conflict (${key.columnName}) do update set
<#list fields as f>
<#if !f.primary>
            ${f.columnName} = excluded.${f.columnName}<#sep>,</#sep>
</#if>
</#list>
<#else>
        on duplicate key update
<#list fields as f>
<#if !f.primary>
        ${f.columnName} = values(${f.columnName})<#sep>,</#sep>
</#if>
</#list>
</#if>
    </insert>

    <!-- 根据主键删除记录 -->
    <delete id="deleteByPrimaryKey" parameterType="${key.javaType}">
        delete from ${tableName} where ${key.columnName} = ${key.param}
    </delete>
</mapper>