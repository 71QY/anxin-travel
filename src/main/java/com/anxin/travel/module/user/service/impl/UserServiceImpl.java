package com.anxin.travel.module.user.service.impl;

import com.anxin.travel.common.util.RedisUtil;
import com.anxin.travel.module.user.dto.CompleteProfileRequest;
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
import org.springframework.transaction.annotation.Transactional;
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
        // 【约束】长辈模式用户不能修改实名信息
        User currentUser = userMapper.selectById(userId);
        if (currentUser != null && currentUser.getGuardMode() != null && currentUser.getGuardMode() == 1) {
            log.warn("⚠️ 长辈模式用户{}尝试修改实名信息", userId);
            throw new RuntimeException("长辈账号不能修改实名信息，请联系绑定的亲友");
        }
        
        // 身份证格式校验
        if (!isValidIdCard(idCard)) {
            throw new RuntimeException("身份证格式不正确");
        }
        
        // 【关键约束】检查身份证号是否已被其他账号绑定（唯一性校验）
        User existingUser = userMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                .eq("id_card", idCard)
                .ne("id", userId)  // 排除自己
        );
        if (existingUser != null) {
            log.warn("⚠️ 身份证{}已被账号{}绑定，userId:{}尝试重复绑定", idCard, existingUser.getId(), userId);
            throw new RuntimeException("该身份证号已被其他账号绑定，不可重复使用");
        }
        
        User user = new User();
        user.setId(userId);
        user.setRealName(realName);
        user.setIdCard(idCard);
        user.setVerified(1);
        userMapper.updateById(user);
        
        log.info("✅ 实名认证成功：userId={}, realName={}, idCard={}", userId, realName, idCard);
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
            throw new RuntimeException("密码最少10位，且必须包含字母和特殊符号");
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
        log.info("🚀 === uploadAvatar 被调用 ===");
        log.info("用户ID: {}", userId);
        
        // 1. 验证文件是否为空
        if (avatarFile == null || avatarFile.isEmpty()) {
            log.error("❌ 头像文件为空");
            throw new IllegalArgumentException("头像文件不能为空");
        }
        
        log.info("文件大小: {} bytes ({} KB)", avatarFile.getSize(), avatarFile.getSize() / 1024);
        log.info("文件名: {}", avatarFile.getOriginalFilename());
        log.info("Content-Type: {}", avatarFile.getContentType());
        
        // 2. 验证文件大小（10MB）
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (avatarFile.getSize() > maxSize) {
            log.error("❌ 图片大小超过限制：{} KB > 10 MB", avatarFile.getSize() / 1024);
            throw new IllegalArgumentException("图片大小超过限制（最大 10MB）");
        }
        
        // 3. 验证文件类型（支持 JPEG/PNG/BMP）
        String contentType = avatarFile.getContentType();
        if (contentType == null || 
            (!contentType.equals("image/jpeg") && 
             !contentType.equals("image/png") && 
             !contentType.equals("image/bmp"))) {
            log.warn("⚠️ 不支持的图片格式：{}", contentType);
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
            
            log.info("📦 开始保存头像...");
            log.info("上传目录: {}", uploadDir);
            log.info("文件名: {}", fileName);
            
            // 5. 确保上传目录存在
            File uploadPath = new File(uploadDir);
            if (!uploadPath.exists()) {
                boolean created = uploadPath.mkdirs();
                log.info("创建上传目录: {}, 结果: {}", uploadDir, created);
            }
            
            // 6. 删除旧头像（如果存在）
            User user = userMapper.selectById(userId);
            if (user != null && user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                String oldFileName = user.getAvatar().substring(user.getAvatar().lastIndexOf("/") + 1);
                String oldFilePath = uploadDir + oldFileName;
                File oldFile = new File(oldFilePath);
                if (oldFile.exists()) {
                    if (oldFile.delete()) {
                        log.info("✅ 已删除旧头像：{}", oldFilePath);
                    } else {
                        log.warn("⚠️ 删除旧头像失败：{}", oldFilePath);
                    }
                }
            }
            
            // 7. 保存新头像
            String filePath = uploadDir + fileName;
            avatarFile.transferTo(new File(filePath));
            log.info("✅ 头像文件已保存：{}", filePath);
            
            // 8. 生成访问 URL
            String avatarUrl = "/api/user/avatar/" + fileName;
            
            // 9. 更新数据库
            User updateUser = new User();
            updateUser.setId(userId);
            updateUser.setAvatar(avatarUrl);
            int updateCount = userMapper.updateById(updateUser);
            
            if (updateCount > 0) {
                log.info("✅ 数据库更新成功：userId={}, avatarUrl={}", userId, avatarUrl);
            } else {
                log.error("❌ 数据库更新失败：userId={}", userId);
                throw new RuntimeException("数据库更新失败");
            }
            
            log.info("✅ 头像上传成功：userId={}, avatarUrl={}, size={}KB", 
                userId, avatarUrl, avatarFile.getSize() / 1024);
            return avatarUrl;
            
        } catch (IllegalArgumentException e) {
            // 参数校验异常，直接抛出
            log.error("❌ 参数校验失败：{}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("❌ 头像上传失败：userId={}", userId, e);
            throw new RuntimeException("头像上传失败，请稍后重试");
        }
    }
    
    private boolean isValidPassword(String password) {
        if (password == null || password.length() < 10) {
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
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeProfile(Long userId, CompleteProfileRequest request) {
        log.info("📝 用户{}请求完善账号信息", userId);
        
        // 1. 参数校验
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            throw new RuntimeException("密码不能为空");
        }
        
        if (!isValidPassword(request.getPassword())) {
            throw new RuntimeException("密码最少10位，且必须包含字母和特殊符号");
        }
        
        if (request.getNickname() == null || request.getNickname().trim().isEmpty()) {
            throw new RuntimeException("昵称不能为空");
        }
        
        String nickname = request.getNickname().trim();
        if (nickname.length() < 1 || nickname.length() > 20) {
            throw new RuntimeException("昵称长度必须在1-20字符之间");
        }
        
        // 2. 查询用户
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 3. 如果已经完善过，不允许再次修改
        if (user.getIsCompleted() != null && user.getIsCompleted() == 1) {
            log.warn("⚠️ 用户{}已完善账号，不允许重复完善", userId);
            throw new RuntimeException("账号已完善，无需重复操作");
        }
        
        // 4. 更新用户信息
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        user.setPassword(encodedPassword);
        user.setNickname(nickname);
        user.setIsCompleted(1);  // 标记为已完善
        userMapper.updateById(user);
        
        log.info("✅ 用户{}账号完善成功，nickname={}", userId, nickname);
    }
}