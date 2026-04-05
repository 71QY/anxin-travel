package com.anxin.travel.module.user.controller;

import com.anxin.travel.common.result.Result;
import com.anxin.travel.common.util.UserContext;
import com.anxin.travel.module.user.dto.ChangePasswordRequest;
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
        String avatarUrl = userService.uploadAvatar(userId, avatarFile);
        return Result.success(avatarUrl);
    }
    
    @GetMapping("/avatar/{filename}")
    public void getAvatar(@PathVariable String filename, HttpServletResponse response) {
        try {
            String filePath = System.getProperty("user.dir") + "/uploads/avatars/" + filename;
            File file = new File(filePath);
            if (!file.exists()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            
            response.setContentType("image/jpeg");
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
        } catch (Exception e) {
            log.error("获取头像失败", e);
            throw new RuntimeException("获取头像失败");
        }
    }
}