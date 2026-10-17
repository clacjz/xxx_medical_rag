package com.clacjz.medical.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clacjz.medical.rag.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.type.EnumTypeHandler;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM users WHERE username = #{username}")
    @Results({
        @Result(property = "role", column = "role", typeHandler = EnumTypeHandler.class),
        @Result(property = "status", column = "status", typeHandler = EnumTypeHandler.class)
    })
    User selectByUsername(@Param("username") String username);

    @Select("SELECT * FROM users WHERE username = #{username}")
    @Results({
        @Result(property = "role", column = "role", typeHandler = EnumTypeHandler.class),
        @Result(property = "status", column = "status", typeHandler = EnumTypeHandler.class)
    })
    User selectByUsernameWithRoles(@Param("username") String username);

    @Select("SELECT * FROM users WHERE id = #{id}")
    @Results({
        @Result(property = "role", column = "role", typeHandler = EnumTypeHandler.class),
        @Result(property = "status", column = "status", typeHandler = EnumTypeHandler.class)
    })
    User selectByIdWithRoles(@Param("id") String id);

    @Select("SELECT * FROM users WHERE email = #{email}")
    @Results({
        @Result(property = "role", column = "role", typeHandler = EnumTypeHandler.class),
        @Result(property = "status", column = "status", typeHandler = EnumTypeHandler.class)
    })
    User selectByEmail(@Param("email") String email);

    @Select("SELECT COUNT(*) FROM users WHERE username = #{username}")
    int existsByUsername(@Param("username") String username);

    @Select("SELECT COUNT(*) FROM users WHERE email = #{email}")
    int existsByEmail(@Param("email") String email);

    @Select("SELECT * FROM users WHERE role = #{role}")
    List<User> selectByRole(@Param("role") String role);
}
