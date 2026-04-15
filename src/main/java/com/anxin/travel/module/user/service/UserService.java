package com.anxin.travel.module.user.service;

import com.anxin.travel.module.user.dto.CompleteProfileRequest;
import com.anxin.travel.module.user.dto.EmergencyContactRequest;
import com.anxin.travel.module.user.dto.UserVO;
import com.anxin.travel.module.user.entity.EmergencyContact;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface UserService {
    UserVO getUserInfo(Long userId);
    void updateUserInfo(Long userId, UserVO userVO);
    void realname(Long userId, String realName, String idCard);
    void addEmergencyContact(Long userId, EmergencyContactRequest request);
    List<EmergencyContact> getEmergencyContacts(Long userId);
    void deleteEmergencyContact(Long userId, Long id);
    void changePassword(Long userId, String phone, String code, String newPassword);
    String uploadAvatar(Long userId, MultipartFile avatarFile);
    void completeProfile(Long userId, CompleteProfileRequest request);  // 完善账号信息
}