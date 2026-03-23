package com.anxin.travel.module.user.controller;

import com.anxin.travel.common.result.Result;
import com.anxin.travel.common.util.UserContext;
import com.anxin.travel.module.user.dto.EmergencyContactRequest;
import com.anxin.travel.module.user.dto.RealnameRequest;
import com.anxin.travel.module.user.dto.UserVO;
import com.anxin.travel.module.user.entity.EmergencyContact;
import com.anxin.travel.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PostMapping("/realname")
    public Result<Void> realname(@RequestBody RealnameRequest request) {
        Long userId = UserContext.getUserId();
        userService.realname(userId, request.getRealName(), request.getIdCard());
        return Result.success();
    }
}