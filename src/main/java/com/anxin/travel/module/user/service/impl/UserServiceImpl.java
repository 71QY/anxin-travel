package com.anxin.travel.module.user.service.impl;

import com.anxin.travel.module.user.dto.EmergencyContactRequest;
import com.anxin.travel.module.user.dto.UserVO;
import com.anxin.travel.module.user.entity.EmergencyContact;
import com.anxin.travel.module.user.entity.User;
import com.anxin.travel.module.user.mapper.EmergencyContactMapper;
import com.anxin.travel.module.user.mapper.UserMapper;
import com.anxin.travel.module.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final EmergencyContactMapper emergencyContactMapper;  // 新增注入

    @Override
    public UserVO getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    public void updateUserInfo(Long userId, UserVO userVO) {
        User user = new User();
        user.setId(userId);
        user.setNickname(userVO.getNickname());
        user.setAvatar(userVO.getAvatar());
        user.setEmergencyContactName(userVO.getEmergencyContactName());
        user.setEmergencyContactPhone(userVO.getEmergencyContactPhone());
        userMapper.updateById(user);
    }

    @Override
    public void addEmergencyContact(Long userId, EmergencyContactRequest request) {
        EmergencyContact contact = new EmergencyContact();
        contact.setUserId(userId);
        contact.setName(request.getName());
        contact.setPhone(request.getPhone());
        emergencyContactMapper.insert(contact);
    }

    @Override
    public List<EmergencyContact> getEmergencyContacts(Long userId) {
        LambdaQueryWrapper<EmergencyContact> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmergencyContact::getUserId, userId)
                .orderByDesc(EmergencyContact::getCreateTime);
        return emergencyContactMapper.selectList(wrapper);
    }
}