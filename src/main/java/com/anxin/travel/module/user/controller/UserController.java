package com.anxin.travel.module.user.controller;

import com.anxin.travel.common.result.Result;
import com.anxin.travel.common.util.UserContext;
import com.anxin.travel.module.user.dto.ChangePasswordRequest;
import com.anxin.travel.module.user.dto.CompleteProfileRequest;
import com.anxin.travel.module.user.dto.EmergencyContactRequest;
import com.anxin.travel.module.user.dto.RealnameRequest;
import com.anxin.travel.module.user.dto.UserVO;
import com.anxin.travel.module.user.entity.EmergencyContact;
import com.anxin.travel.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public Result<UserVO> getProfile() {
        Long userId = UserContext.getUserId();
        return Result.success(userService.getUserInfo(userId));
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody UserVO userVO) {
        Long userId = UserContext.getUserId();
        userService.updateUserInfo(userId, userVO);
        return Result.success();
    }

    @PostMapping("/emergency")
    public Result<Void> addEmergencyContact(@RequestBody EmergencyContactRequest request) {
        Long userId = UserContext.getUserId();
        userService.addEmergencyContact(userId, request);
        return Result.success();
    }

    @GetMapping("/emergency")
    public Result<List<EmergencyContact>> getEmergencyContacts() {
        Long userId = UserContext.getUserId();
        return Result.success(userService.getEmergencyContacts(userId));
    }

    @DeleteMapping("/emergency/{id}")
    public Result<Void> deleteEmergencyContact(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        userService.deleteEmergencyContact(userId, id);
        return Result.success();
    }

    @PostMapping("/realname")
    public Result<Void> realname(@RequestBody RealnameRequest request) {
        Long userId = UserContext.getUserId();
        userService.realname(userId, request.getRealName(), request.getIdCard());
        return Result.success();
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        Long userId = UserContext.getUserId();
        userService.changePassword(userId, request.getPhone(), request.getCode(), request.getNewPassword());
        return Result.success();
    }
    
    /**
     * 用户上传头像
     * 【关键修复】参数名改为 avatar，与前端文档保持一致
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("avatar") MultipartFile avatarFile) {
        Long userId = UserContext.getUserId();
        
        // 【新增】详细日志，便于排查问题
        log.info("=== 收到头像上传请求 ===");
        log.info("用户ID: {}", userId);
        if (avatarFile != null) {
            log.info("文件大小: {} bytes ({} KB)", avatarFile.getSize(), avatarFile.getSize() / 1024);
            log.info("文件名: {}", avatarFile.getOriginalFilename());
            log.info("Content-Type: {}", avatarFile.getContentType());
        } else {
            log.warn("⚠️ avatarFile 为 null");
        }
        
        String avatarUrl = userService.uploadAvatar(userId, avatarFile);
        
        log.info("✅ 头像上传成功，返回 URL: {}", avatarUrl);
        return Result.success(avatarUrl);
    }
    
    @GetMapping("/avatar/{filename}")
    public void getAvatar(@PathVariable String filename, HttpServletResponse response) {
        log.info("📥 收到头像获取请求：filename={}", filename);
        
        try {
            String filePath = System.getProperty("user.dir") + "/uploads/avatars/" + filename;
            log.info("文件路径: {}", filePath);
            
            File file = new File(filePath);
            if (!file.exists()) {
                log.warn("⚠️ 头像文件不存在：{}", filePath);
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            
            // 根据文件扩展名设置 Content-Type
            String contentType = "image/jpeg"; // 默认
            if (filename.toLowerCase().endsWith(".png")) {
                contentType = "image/png";
            } else if (filename.toLowerCase().endsWith(".bmp")) {
                contentType = "image/bmp";
            }
            
            response.setContentType(contentType);
            response.setContentLengthLong(file.length());
            
            log.info("✅ 开始返回头像：size={} bytes", file.length());
            
            FileInputStream fis = new FileInputStream(file);
            OutputStream os = response.getOutputStream();
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            
            fis.close();
            os.flush();
            os.close();
            
            log.info("✅ 头像返回成功");
        } catch (Exception e) {
            log.error("❌ 获取头像失败：filename={}", filename, e);
            throw new RuntimeException("获取头像失败");
        }
    }
    
    /**
     * 完善账号信息（设置密码和昵称）
     * POST /api/user/complete-profile
     */
    @PostMapping("/complete-profile")
    public Result<Void> completeProfile(@RequestBody CompleteProfileRequest request) {
        try {
            Long userId = UserContext.getUserId();
            log.info("📝 用户{}请求完善账号信息", userId);
            
            userService.completeProfile(userId, request);
            
            log.info("✅ 用户{}账号完善成功", userId);
            return Result.success();
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ 完善账号参数错误：{}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (RuntimeException e) {
            log.error("❌ 完善账号失败：{}", e.getMessage());
            return Result.error(500, e.getMessage());
        }
    }
}