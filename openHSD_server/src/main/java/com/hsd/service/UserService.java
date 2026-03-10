package com.hsd.service;

import java.util.Map;

public interface UserService {

    Map<String, Object> login(String username, String password);

    void register(String username, String password);
}
