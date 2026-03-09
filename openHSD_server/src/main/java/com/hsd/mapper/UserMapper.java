package com.hsd.mapper;

import com.hsd.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("SELECT id, username, password_hash, created_at FROM users WHERE username = #{username} LIMIT 1")
    User findByUsername(String username);

    @Select("SELECT id, username, password_hash, created_at FROM users WHERE id = #{id} LIMIT 1")
    User findById(Long id);
}
