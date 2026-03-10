package com.hsd.mapper;

import com.hsd.entity.User;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {

    @Select("SELECT id, username, password_hash, created_at FROM users WHERE username = #{username} LIMIT 1")
    User findByUsername(String username);

    @Select("SELECT id, username, password_hash, created_at FROM users WHERE id = #{id} LIMIT 1")
    User findById(Long id);

    @Insert("INSERT INTO users (username, password_hash, created_at) VALUES (#{username}, #{passwordHash}, NOW())")
    int insert(@Param("username") String username, @Param("passwordHash") String passwordHash);
}
