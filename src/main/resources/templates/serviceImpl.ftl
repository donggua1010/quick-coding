package ${implpkg};

import ${epkg}.${entity};
import ${mpkg}.${entity}Mapper;
import ${spkg}.${entity}Service;
<#if key.needsImport>
import ${key.javaType};
</#if>
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

/**
 * ${entity} 业务服务实现
 * <p>委托 ${entity}Mapper 完成数据访问（自动生成）。</p>
 */
@Service
public class ${entity}ServiceImpl implements ${entity}Service {

    /** 数据访问组件 */
    @Autowired
    private ${entity}Mapper ${mapperVar};

    /** {@inheritDoc} */
    @Override
    public ${entity} selectByPrimaryKey(${key.javaType} ${key.fieldName}) {
        return ${mapperVar}.selectByPrimaryKey(${key.fieldName});
    }

    /** {@inheritDoc} */
    @Override
    public List<${entity}> selectList(${entity} record) {
        return ${mapperVar}.selectList(record);
    }

    /** {@inheritDoc} */
    @Override
    public int insert(${entity} entity) {
        return ${mapperVar}.insert(entity);
    }

    /** {@inheritDoc} */
    @Override
    public int updateByPrimaryKey(${entity} entity) {
        return ${mapperVar}.updateByPrimaryKey(entity);
    }

    /** {@inheritDoc} */
    @Override
    public int deleteByPrimaryKey(${key.javaType} ${key.fieldName}) {
        return ${mapperVar}.deleteByPrimaryKey(${key.fieldName});
    }
}