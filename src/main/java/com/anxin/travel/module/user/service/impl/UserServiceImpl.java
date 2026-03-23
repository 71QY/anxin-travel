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
    private final EmergencyContactMapper emergencyContactMapper;

    // 身份证校验（可提取为工具类）
    private boolean isValidIdCard(String idCard) {
        return idCard != null && idCard.matches("^[1-9]\\d{5}(18|19|20)?\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}(\\d|X|x)$");
    }

    @Override
    public UserVO getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        // 确保 verified 字段被复制（BeanUtils 会自动复制同名字段）
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

    @Override
    public void realname(Long userId, String realName, String idCard) {
        // 身份证格式校验
        if (!isValidIdCard(idCard)) {
            throw new RuntimeException("身份证格式不正确");
        }
        User user = new User();
        user.setId(userId);
        user.setRealName(realName);
        user.setIdCard(idCard);
        user.setVerified(1);
        userMapper.updateById(user);
    }
}