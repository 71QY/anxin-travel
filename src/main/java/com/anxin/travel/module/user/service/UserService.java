package com.anxin.travel.module.user.service;

import com.anxin.travel.module.user.dto.UserVO;

public interface UserService {
    UserVO getUserInfo(Long userId);
    void updateUserInfo(Long userId, UserVO userVO);
}