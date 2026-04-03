package com.anxin.travel.module.user.service.impl;

import com.anxin.travel.common.util.RedisUtil;
import com.anxin.travel.module.user.dto.EmergencyContactRequest;
import com.anxin.travel.module.user.dto.UserVO;
import com.anxin.travel.module.user.entity.EmergencyContact;
import com.anxin.travel.module.user.entity.User;
import com.anxin.travel.module.user.mapper.EmergencyContactMapper;
import com.anxin.travel.module.user.mapper.UserMapper;
import com.anxin.travel.module.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final EmergencyContactMapper emergencyContactMapper;
    private final RedisUtil redisUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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
    public void deleteEmergencyContact(Long userId, Long id) {
        EmergencyContact contact = emergencyContactMapper.selectById(id);
        if (contact == null) {
            throw new RuntimeException("联系人不存在");
        }
        if (!contact.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除他人联系人");
        }
        emergencyContactMapper.deleteById(id);
        log.info("紧急联系人已删除，userId: {}, contactId: {}", userId, id);
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

    @Override
    public void changePassword(Long userId, String phone, String code, String newPassword) {
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        
        if (phone == null || phone.isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }
        
        if (newPassword == null || newPassword.isEmpty()) {
            throw new RuntimeException("新密码不能为空");
        }
        
        if (!isValidPassword(newPassword)) {
            throw new RuntimeException("密码必须为 8 位，且包含字母和特殊符号");
        }
        
        String codeKey = "sms:code:" + phone;
        String redisCode = redisUtil.get(codeKey);
        if (redisCode == null || !redisCode.equals(code)) {
            throw new RuntimeException("验证码错误");
        }
        
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        if (!user.getPhone().equals(phone)) {
            throw new RuntimeException("手机号不匹配");
        }
        
        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);
        userMapper.updateById(user);
        
        redisUtil.deleteSingle(codeKey);
        log.info("密码修改成功，userId={}, phone={}", userId, phone);
    }
    
    @Override
    public String uploadAvatar(Long userId, MultipartFile avatarFile) {
        if (avatarFile == null || avatarFile.isEmpty()) {
            throw new RuntimeException("头像文件不能为空");
        }
        
        String contentType = avatarFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("只能上传图片文件");
        }
        
        long maxSize = 5 * 1024 * 1024;
        if (avatarFile.getSize() > maxSize) {
            throw new RuntimeException("图片大小不能超过 5MB");
        }
        
        try {
            String fileName = "avatar_" + userId + "_" + System.currentTimeMillis() + ".jpg";
            String uploadDir = System.getProperty("user.dir") + "/uploads/avatars/";
            
            File uploadPath = new File(uploadDir);
            if (!uploadPath.exists()) {
                uploadPath.mkdirs();
            }
            
            String filePath = uploadDir + fileName;
            avatarFile.transferTo(new File(filePath));
            
            String avatarUrl = "/api/user/avatar/" + fileName;
            
            User user = new User();
            user.setId(userId);
            user.setAvatar(avatarUrl);
            userMapper.updateById(user);
            
            log.info("头像上传成功，userId={}, avatarUrl={}", userId, avatarUrl);
            return avatarUrl;
        } catch (Exception e) {
            log.error("头像上传失败", e);
            throw new RuntimeException("头像上传失败：" + e.getMessage());
        }
    }
    
    private boolean isValidPassword(String password) {
        if (password == null || password.length() != 8) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasSymbol = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (!Character.isDigit(c)) {
                hasSymbol = true;
            }
        }
        return hasLetter && hasSymbol;
    }
}