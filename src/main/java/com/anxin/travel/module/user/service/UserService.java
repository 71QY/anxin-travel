package com.anxin.travel.module.user.service;

import com.anxin.travel.module.user.dto.EmergencyContactRequest;
import com.anxin.travel.module.user.dto.UserVO;
import com.anxin.travel.module.user.entity.EmergencyContact;
import java.util.List;

public interface UserService {
    UserVO getUserInfo(Long userId);
    void updateUserInfo(Long userId, UserVO userVO);

    // 新增方法
    void addEmergencyContact(Long userId, EmergencyContactRequest request);
    List<EmergencyContact> getEmergencyContacts(Long userId);
}