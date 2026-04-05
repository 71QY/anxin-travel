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
        // 1. 验证文件是否为空
        if (avatarFile == null || avatarFile.isEmpty()) {
            throw new IllegalArgumentException("头像文件不能为空");
        }
        
        // 2. 验证文件大小（10MB）
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (avatarFile.getSize() > maxSize) {
            throw new IllegalArgumentException("图片大小超过限制（最大 10MB）");
        }
        
        // 3. 验证文件类型（支持 JPEG/PNG/BMP）
        String contentType = avatarFile.getContentType();
        if (contentType == null || 
            (!contentType.equals("image/jpeg") && 
             !contentType.equals("image/png") && 
             !contentType.equals("image/bmp"))) {
            log.warn("不支持的图片格式：{}", contentType);
            throw new IllegalArgumentException("不支持的图片格式，仅支持 JPEG/PNG/BMP");
        }
        
        try {
            // 4. 生成唯一文件名
            String originalFilename = avatarFile.getOriginalFilename();
            String extension = ".jpg"; // 默认扩展名
            
            if (originalFilename != null && originalFilename.contains(".")) {
                String ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
                if (ext.equals(".png") || ext.equals(".bmp")) {
                    extension = ext;
                }
            }
            
            String fileName = "user_" + userId + "_" + System.currentTimeMillis() + extension;
            String uploadDir = System.getProperty("user.dir") + "/uploads/avatars/";
            
            // 5. 确保上传目录存在
            File uploadPath = new File(uploadDir);
            if (!uploadPath.exists()) {
                uploadPath.mkdirs();
            }
            
            // 6. 删除旧头像（如果存在）
            User user = userMapper.selectById(userId);
            if (user != null && user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                String oldFileName = user.getAvatar().substring(user.getAvatar().lastIndexOf("/") + 1);
                String oldFilePath = uploadDir + oldFileName;
                File oldFile = new File(oldFilePath);
                if (oldFile.exists() && oldFile.delete()) {
                    log.info("✅ 已删除旧头像：{}", oldFilePath);
                }
            }
            
            // 7. 保存新头像
            String filePath = uploadDir + fileName;
            avatarFile.transferTo(new File(filePath));
            
            // 8. 生成访问 URL
            String avatarUrl = "/api/user/avatar/" + fileName;
            
            // 9. 更新数据库
            User updateUser = new User();
            updateUser.setId(userId);
            updateUser.setAvatar(avatarUrl);
            userMapper.updateById(updateUser);
            
            log.info("✅ 头像上传成功：userId={}, avatarUrl={}, size={}KB", 
                userId, avatarUrl, avatarFile.getSize() / 1024);
            return avatarUrl;
            
        } catch (IllegalArgumentException e) {
            // 参数校验异常，直接抛出
            throw e;
        } catch (Exception e) {
            log.error("❌ 头像上传失败：userId={}", userId, e);
            throw new RuntimeException("头像上传失败，请稍后重试");
        }
    }
    
    private boolean isValidPassword(String password) {
        if (password == null || password.length() != 10) {
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