package com.clacjz.medical.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clacjz.medical.rag.model.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {

    @Select("SELECT * FROM sys_config WHERE category = #{category} ORDER BY config_key")
    List<SysConfig> selectByCategory(@Param("category") String category);

    @Select("SELECT * FROM sys_config WHERE config_key = #{configKey}")
    SysConfig selectByConfigKey(@Param("configKey") String configKey);

    @Select("SELECT * FROM sys_config ORDER BY category, config_key")
    List<SysConfig> selectAll();
}