package com.anxin.travel.module.user.controller;

import com.anxin.travel.common.result.Result;
import com.anxin.travel.common.util.UserContext;
import com.anxin.travel.module.user.dto.EmergencyContactVO;
import com.anxin.travel.module.user.entity.EmergencyContact;
import com.anxin.travel.module.user.mapper.EmergencyContactMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user/emergency")
@RequiredArgsConstructor
public class EmergencyContactController {

    private final EmergencyContactMapper emergencyContactMapper;   // 添加 final

    @PostMapping
    public Result<Void> addEmergencyContact(@RequestBody EmergencyContactVO vo) {
        Long userId = UserContext.getUserId();
        EmergencyContact contact = new EmergencyContact();
        contact.setUserId(userId);
        contact.setName(vo.getName());
        contact.setPhone(vo.getPhone());
        emergencyContactMapper.insert(contact);
        return Result.success();
    }

    @GetMapping
    public Result<List<EmergencyContactVO>> getEmergencyContacts() {
        Long userId = UserContext.getUserId();
        List<EmergencyContact> list = emergencyContactMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<EmergencyContact>()
                        .eq("user_id", userId));
        List<EmergencyContactVO> result = list.stream().map(c -> {
            EmergencyContactVO vo = new EmergencyContactVO();
            vo.setId(c.getId());
            vo.setName(c.getName());
            vo.setPhone(c.getPhone());
            return vo;
        }).collect(Collectors.toList());
        return Result.success(result);
    }
}