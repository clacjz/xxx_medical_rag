package com.clacjz.medical.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clacjz.medical.rag.model.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Select("SELECT * FROM chat_sessions WHERE user_id = #{userId} ORDER BY updated_at DESC")
    List<ChatSession> selectByUserId(@Param("userId") String userId);

    @Select("SELECT * FROM chat_sessions WHERE department_id = #{departmentId} ORDER BY updated_at DESC")
    List<ChatSession> selectByDepartmentId(@Param("departmentId") String departmentId);

    @Select("SELECT * FROM chat_sessions ORDER BY updated_at DESC")
    List<ChatSession> selectAll();
}