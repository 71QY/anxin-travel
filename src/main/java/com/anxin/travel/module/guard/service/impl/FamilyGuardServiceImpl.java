package com.anxin.travel.module.guard.service.impl;

import com.anxin.travel.agent.controller.NativeWebSocket;
import com.anxin.travel.common.result.Result;
import com.anxin.travel.module.chat.entity.OrderChat;
import com.anxin.travel.module.chat.mapper.OrderChatMapper;
import com.anxin.travel.module.guard.dto.AddGuardianRequest;
import com.anxin.travel.module.guard.dto.BindExistingElderRequest;
import com.anxin.travel.module.guard.dto.ConfirmProxyOrderRequest;
import com.anxin.travel.module.guard.dto.ProxyOrderRequest;
import com.anxin.travel.module.guard.dto.RegisterElderRequest;
import com.anxin.travel.module.guard.entity.FamilyGuard;
import com.anxin.travel.module.guard.mapper.FamilyGuardMapper;
import com.anxin.travel.module.guard.service.FamilyGuardService;
import com.anxin.travel.module.order.entity.OrderInfo;
import com.anxin.travel.module.order.mapper.OrderMapper;
import com.anxin.travel.module.user.entity.User;
import com.anxin.travel.module.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class FamilyGuardServiceImpl implements FamilyGuardService {

    @Autowired
    private FamilyGuardMapper familyGuardMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderChatMapper orderChatMapper;
    @Autowired
    private NativeWebSocket nativeWebSocket;
    @Autowired
    private com.anxin.travel.module.order.service.DriverAssignmentService driverAssignmentService;

    @Override
    @Transactional
    public Result<?> addGuardian(Long guardianId, AddGuardianRequest request) {
        // 1. 空值检查
        if (request.getElderPhone() == null || request.getElderPhone().trim().isEmpty()) {
            return Result.error("长辈手机号不能为空");
        }
        if (request.getGuardianName() == null || request.getGuardianName().trim().isEmpty()) {
            return Result.error("亲友姓名不能为空");
        }
        if (request.getGuardianIdCard() == null || request.getGuardianIdCard().trim().isEmpty()) {
            return Result.error("亲友身份证号不能为空");
        }
        
        // 2. 格式校验
        if (!request.getElderPhone().matches("^1[3-9]\\d{9}$")) {
            return Result.error("长辈手机号格式错误");
        }
        if (!request.getGuardianIdCard().matches("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$")) {
            return Result.error("身份证号格式错误");
        }
        
        // 3. 【新增约束】普通用户不能绑定自己为长辈账号
        User guardian = userMapper.selectById(guardianId);
        if (guardian == null) {
            return Result.error("用户不存在");
        }
        if (guardian.getPhone().equals(request.getElderPhone())) {
            log.warn("⚠️ 用户{}尝试绑定自己为长辈，手机号：{}", guardianId, request.getElderPhone());
            return Result.error("不能绑定自己的账号为长辈，请使用其他家人的手机号");
        }
        
        // 4. 检查亲友模式：只有普通模式用户才能添加绑定（长辈模式用户不允许）
        if (guardian.getGuardMode() != null && guardian.getGuardMode() == 1) {
            log.warn("⚠️ 长辈模式用户{}尝试添加绑定", guardianId);
            return Result.error("长辈模式无法添加绑定，请切换为普通账号");
        }

        // 5. 【约束】检查该长辈已被多少人绑定（最多4人）
        // 先查询该手机号是否已有用户ID
        User elderUser = userMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                .eq("phone", request.getElderPhone())
        );
        
        if (elderUser != null) {
            // 如果长辈已注册，检查其被绑定数量
            int elderBindCount = familyGuardMapper.countByElderId(elderUser.getId());
            if (elderBindCount >= 4) {
                log.warn("⚠️ 长辈{}已被{}人绑定，达到上限4人", elderUser.getId(), elderBindCount);
                return Result.error("该长辈已被4位亲友绑定，无法继续绑定");
            }
        } else {
            // 如果长辈未注册，检查待激活状态的绑定数量
            List<FamilyGuard> pendingBindings = familyGuardMapper.selectPendingByElderPhone(request.getElderPhone());
            if (pendingBindings.size() >= 4) {
                log.warn("⚠️ 长辈手机号{}已有{}个待激活绑定，达到上限4人", request.getElderPhone(), pendingBindings.size());
                return Result.error("该手机号已有4位亲友申请绑定，请等待长辈激活");
            }
        }

        // 6. 【约束】检查亲友已绑定的长辈数量（最多99人）
        int guardianBindCount = familyGuardMapper.countByGuardianId(guardianId);
        if (guardianBindCount >= 99) {
            log.warn("⚠️ 亲友{}已绑定{}个长辈，达到上限99人", guardianId, guardianBindCount);
            return Result.error("您已绑定99个长辈，达到上限，请先解绑部分长辈");
        }

        // 7. 检查该手机号是否已被其他亲友绑定
        FamilyGuard existing = familyGuardMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FamilyGuard>()
                .eq("elder_phone", request.getElderPhone())
                .in("status", Arrays.asList(0, 1))
        );
        if (existing != null) {
            return Result.error("该手机号已被其他亲友绑定");
        }
        
        // 8. 插入绑定记录
        FamilyGuard guard = new FamilyGuard();
        guard.setGuardianId(guardianId);
        guard.setElderPhone(request.getElderPhone());
        guard.setElderName(request.getElderName());
        guard.setGuardianName(request.getGuardianName());
        guard.setGuardianIdCard(request.getGuardianIdCard());
        guard.setGuardianPhone(guardian.getPhone());
        guard.setStatus(0);  // 待激活
        familyGuardMapper.insert(guard);

        log.info("✅ 亲友{}绑定长辈{}成功，状态：待激活", guardianId, request.getElderPhone());
        return Result.success("绑定成功，请告知长辈使用该手机号登录");
    }

    /**
     * 亲友帮长辈注册账号（新接口）
     */
    @Override
    @Transactional
    public Result<?> registerElder(Long guardianId, RegisterElderRequest request) {
        log.info("👨‍‍👧 亲友{}帮长辈注册账号", guardianId);
        
        // 1. 空值检查
        if (request.getElderName() == null || request.getElderName().trim().isEmpty()) {
            return Result.error("长辈姓名不能为空");
        }
        if (request.getElderIdCard() == null || request.getElderIdCard().trim().isEmpty()) {
            return Result.error("长辈身份证号不能为空");
        }
        if (request.getElderPhone() == null || request.getElderPhone().trim().isEmpty()) {
            return Result.error("长辈手机号不能为空");
        }
        
        // 2. 格式校验
        if (!request.getElderPhone().matches("^1[3-9]\\d{9}$")) {
            return Result.error("长辈手机号格式错误");
        }
        if (!request.getElderIdCard().matches("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$")) {
            return Result.error("身份证号格式错误");
        }
        
        // 3. 检查亲友用户是否存在
        User guardian = userMapper.selectById(guardianId);
        if (guardian == null) {
            return Result.error("用户不存在");
        }
        
        // 4. 检查亲友模式：只有普通模式用户才能帮注册
        if (guardian.getGuardMode() != null && guardian.getGuardMode() == 1) {
            return Result.error("长辈模式无法帮长辈注册，请切换为普通账号");
        }
        
        // 5. 【关键约束】检查身份证号是否已被任何账号绑定
        User existingUserByIdCard = userMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                .eq("id_card", request.getElderIdCard())
        );
        if (existingUserByIdCard != null) {
            log.warn("⚠️ 身份证{}已被账号{}绑定，不可重复注册", request.getElderIdCard(), existingUserByIdCard.getId());
            return Result.error("该身份证已绑定账号，不可重复注册");
        }
        
        // 6. 检查长辈手机号是否已注册
        User existingUserByPhone = userMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                .eq("phone", request.getElderPhone())
        );
        if (existingUserByPhone != null) {
            log.warn("⚠️ 手机号{}已存在账号{}", request.getElderPhone(), existingUserByPhone.getId());
            return Result.error("该手机号已存在账号，不可帮注册");
        }
        
        // 7. 【自动创建长辈账号】
        User elderUser = new User();
        elderUser.setPhone(request.getElderPhone());
        elderUser.setIdCard(request.getElderIdCard());
        elderUser.setRealName(request.getElderName());
        elderUser.setIsGuarded(1);  // 被守护
        elderUser.setGuardMode(1);  // 长辈模式
        elderUser.setVerified(0);   // 未实名认证（由亲友帮实名）
        userMapper.insert(elderUser);
        
        log.info("✅ 长辈账号创建成功，用户ID: {}, 手机号: {}", elderUser.getId(), elderUser.getPhone());
        
        // 8. 【自动建立绑定关系】
        FamilyGuard guard = new FamilyGuard();
        guard.setGuardianId(guardianId);
        guard.setElderId(elderUser.getId());
        guard.setElderPhone(request.getElderPhone());
        guard.setElderName(request.getElderName());
        guard.setElderIdCard(request.getElderIdCard());
        guard.setGuardianName(guardian.getRealName() != null ? guardian.getRealName() : guardian.getNickname());
        guard.setGuardianIdCard(guardian.getIdCard());
        guard.setGuardianPhone(guardian.getPhone());
        guard.setStatus(1);  // 直接已绑定（无需等待激活）
        guard.setActivateTime(LocalDateTime.now());
        familyGuardMapper.insert(guard);
        
        log.info("✅ 自动绑定成功，亲友{} 绑定长辈{}", guardianId, elderUser.getId());
        
        return Result.success("长辈账号已创建成功，让长辈用该手机号接收验证码登录即可直接使用");
    }

    /**
     * 绑定已有的长辈账号（新接口）
     */
    @Override
    @Transactional
    public Result<?> bindExistingElder(Long guardianId, BindExistingElderRequest request) {
        log.info("👨‍👩‍ 亲友{}绑定已有长辈账号", guardianId);
        
        // 1. 空值检查
        if (request.getElderPhone() == null || request.getElderPhone().trim().isEmpty()) {
            return Result.error("长辈手机号不能为空");
        }
        if (request.getElderName() == null || request.getElderName().trim().isEmpty()) {
            return Result.error("长辈姓名不能为空");
        }
        if (request.getElderIdCard() == null || request.getElderIdCard().trim().isEmpty()) {
            return Result.error("长辈身份证号不能为空");
        }
        
        // 2. 格式校验
        if (!request.getElderPhone().matches("^1[3-9]\\d{9}$")) {
            return Result.error("长辈手机号格式错误");
        }
        if (!request.getElderIdCard().matches("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$")) {
            return Result.error("身份证号格式错误");
        }
        
        // 3. 检查亲友用户是否存在
        User guardian = userMapper.selectById(guardianId);
        if (guardian == null) {
            return Result.error("用户不存在");
        }
        
        // 4. 检查亲友模式：只有普通模式用户才能绑定
        if (guardian.getGuardMode() != null && guardian.getGuardMode() == 1) {
            return Result.error("长辈模式无法绑定长辈账号，请切换为普通账号");
        }
        
        // 5. 检查长辈账号是否存在
        User elderUser = userMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                .eq("phone", request.getElderPhone())
        );
        if (elderUser == null) {
            return Result.error("该手机号未注册长辈账号，请先帮长辈注册");
        }
        
        // 6. 【关键校验】检查是否是长辈模式账号
        if (elderUser.getGuardMode() == null || elderUser.getGuardMode() != 1) {
            return Result.error("该账号不是长辈模式，无法绑定");
        }
        
        // 7. 【关键校验】身份证必须匹配
        if (!elderUser.getIdCard().equals(request.getElderIdCard())) {
            log.warn("⚠️ 身份证不匹配，账号身份证: {}, 输入身份证: {}", elderUser.getIdCard(), request.getElderIdCard());
            return Result.error("身份证号与账号信息不匹配，请核对后重试");
        }
        
        // 8. 检查该长辈已被多少人绑定（最多4人）
        int elderBindCount = familyGuardMapper.countByElderId(elderUser.getId());
        if (elderBindCount >= 4) {
            log.warn("⚠️ 长辈{}已被{}人绑定，达到上限4人", elderUser.getId(), elderBindCount);
            return Result.error("该长辈已被4位亲友绑定，无法继续绑定");
        }
        
        // 9. 检查该亲友是否已绑定该长辈
        FamilyGuard existingBind = familyGuardMapper.selectByGuardianAndElder(guardianId, elderUser.getId());
        if (existingBind != null) {
            return Result.error("您已绑定该长辈账号");
        }
        
        // 10. 建立绑定关系
        FamilyGuard guard = new FamilyGuard();
        guard.setGuardianId(guardianId);
        guard.setElderId(elderUser.getId());
        guard.setElderPhone(request.getElderPhone());
        guard.setElderName(request.getElderName());
        guard.setElderIdCard(request.getElderIdCard());
        guard.setGuardianName(guardian.getRealName() != null ? guardian.getRealName() : guardian.getNickname());
        guard.setGuardianIdCard(guardian.getIdCard());
        guard.setGuardianPhone(guardian.getPhone());
        guard.setStatus(1);  // 直接已绑定
        guard.setActivateTime(LocalDateTime.now());
        familyGuardMapper.insert(guard);
        
        log.info("✅ 亲友{}绑定长辈账号{}成功", guardianId, elderUser.getId());
        return Result.success("绑定成功");
    }

    @Override
    @Transactional(readOnly = true)
    public Result<?> getMyElders(Long guardianId) {
        log.info("👨‍👩‍👧 亲友{}查询长辈列表", guardianId);
        
        List<FamilyGuard> list = familyGuardMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FamilyGuard>()
                .eq("guardian_id", guardianId)
                .in("status", Arrays.asList(0, 1))
                .orderByDesc("bind_time")
        );
        
        // 转换为前端期望的DTO格式
        List<com.anxin.travel.module.guard.dto.ElderInfo> elderInfos = list.stream()
            .map(guard -> {
                com.anxin.travel.module.guard.dto.ElderInfo info = new com.anxin.travel.module.guard.dto.ElderInfo();
                info.setGuardId(guard.getId());
                info.setElderId(guard.getElderId());
                info.setElderName(guard.getElderName());
                info.setElderPhone(guard.getElderPhone());
                info.setElderIdCard(guard.getElderIdCard());
                info.setStatus(guard.getStatus());
                info.setBindTime(guard.getBindTime());
                info.setActivateTime(guard.getActivateTime());
                return info;
            })
            .collect(java.util.stream.Collectors.toList());
        
        log.info("✅ 查询长辈列表成功：guardianId={}, count={}", guardianId, elderInfos.size());
        return Result.success(elderInfos);
    }

    @Override
    @Transactional
    public Result<?> batchActivateGuardians(Long elderId, String elderPhone) {
        // 1. 查询该手机号所有待激活绑定
        List<FamilyGuard> pendingGuards = familyGuardMapper.selectPendingByElderPhone(elderPhone);
        if (pendingGuards.isEmpty()) {
            return Result.success(null);  // 没有待激活的绑定
        }

        // 2. 【关键修复】检查总人数不超4人
        if (pendingGuards.size() > 4) {
            log.warn("⚠️ 长辈{}待激活绑定数{}超过上限4人", elderId, pendingGuards.size());
            return Result.error("绑定人数超过上限4人，请联系部分亲友解绑后重试");
        }

        // 3. 【新增】检查已激活的绑定数量 + 待激活数量 <= 4
        int activatedCount = familyGuardMapper.countByElderId(elderId);
        int totalCount = activatedCount + pendingGuards.size();
        
        if (totalCount > 4) {
            log.warn("⚠️ 长辈{}已有{}个激活绑定，加上{}个待激活，总数{}超过上限4人", 
                elderId, activatedCount, pendingGuards.size(), totalCount);
            return Result.error("该长辈已被" + activatedCount + "位亲友绑定，最多只能绑定4位");
        }

        // 4. 批量激活所有待激活记录
        for (FamilyGuard guard : pendingGuards) {
            guard.setElderId(elderId);
            guard.setStatus(1);
            guard.setActivateTime(LocalDateTime.now());
            familyGuardMapper.updateById(guard);
        }

        // 5. 设置长辈模式标识
        User user = userMapper.selectById(elderId);
        user.setIsGuarded(1);
        user.setGuardMode(1);
        userMapper.updateById(user);

        log.info("✅ 长辈{}登录，批量激活{}个亲友绑定，当前总绑定数：{}", 
            elderId, pendingGuards.size(), totalCount);
        return Result.success("激活成功，当前共有" + totalCount + "位亲友绑定");
    }

    @Override
    @Transactional(readOnly = true)
    public Result<?> getMyGuardians(Long elderId) {
        List<FamilyGuard> list = familyGuardMapper.selectActiveGuardsByElderId(elderId);
        
        // 转换为前端期望的DTO格式
        List<com.anxin.travel.module.guard.dto.GuardianInfo> guardianInfos = list.stream()
            .map(guard -> {
                com.anxin.travel.module.guard.dto.GuardianInfo info = new com.anxin.travel.module.guard.dto.GuardianInfo();
                info.setGuardianId(guard.getGuardianId());
                
                // 脱敏处理姓名
                String name = guard.getGuardianName();
                if (name != null && name.length() > 1) {
                    info.setGuardianName(name.charAt(0) + "*");
                    info.setRealName(name.charAt(0) + "*");
                } else {
                    info.setGuardianName(name);
                    info.setRealName(name);
                }
                
                // 脱敏处理手机号
                String phone = guard.getGuardianPhone();
                if (phone != null && phone.length() > 7) {
                    info.setGuardianPhone(phone.substring(0, 3) + "****" + phone.substring(7));
                } else {
                    info.setGuardianPhone(phone);
                }
                
                info.setBindTime(guard.getActivateTime() != null ? guard.getActivateTime() : guard.getBindTime());
                
                return info;
            })
            .collect(java.util.stream.Collectors.toList());
        
        log.info("✅ 查询亲友列表成功：elderId={}, count={}", elderId, guardianInfos.size());
        return Result.success(guardianInfos);
    }

    @Override
    @Transactional
    public Result<?> unbindAll(Long elderId) {
        // 1. 更新所有绑定记录为已解绑
        familyGuardMapper.update(null,
            new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<FamilyGuard>()
                .eq("elder_id", elderId)
                .eq("status", 1)
                .set("status", 2)
                .set("unbind_time", LocalDateTime.now())
        );

        // 2. 恢复用户为普通模式
        User user = userMapper.selectById(elderId);
        user.setIsGuarded(0);
        user.setGuardMode(0);
        userMapper.updateById(user);

        log.info("✅ 长辈{}一键解绑所有亲友", elderId);
        return Result.success("已解绑所有亲友，恢复普通模式");
    }

    @Override
    @Transactional
    public Result<?> unbindOne(Long guardId, Long guardianId) {
        FamilyGuard guard = familyGuardMapper.selectById(guardId);
        if (guard == null || !guard.getGuardianId().equals(guardianId)) {
            return Result.error("绑定记录不存在");
        }

        guard.setStatus(2);
        guard.setUnbindTime(LocalDateTime.now());
        familyGuardMapper.updateById(guard);

        log.info("✅ 亲友{}解绑长辈{}，绑定记录ID: {}", guardianId, guard.getElderId(), guardId);
        return Result.success("解绑成功");
    }

    @Override
    @Transactional
    public Result<?> proxyOrder(Long guardianId, ProxyOrderRequest request) {
        log.info("🚗 代叫车请求：guardianId={}, elderId={}, destAddress={}", 
                guardianId, request.getElderId(), request.getDestAddress());
        
        // 0. 防止长辈给自己代叫车
        if (guardianId.equals(request.getElderId())) {
            log.warn("⚠️ 用户{}尝试给自己代叫车", guardianId);
            return Result.error("不能给自己代叫车，请使用普通用户账号为长辈代叫");
        }
        
        // 1. 校验绑定关系
        FamilyGuard guard = familyGuardMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FamilyGuard>()
                .eq("guardian_id", guardianId)
                .eq("elder_id", request.getElderId())
                .eq("status", 1)
        );
        if (guard == null) {
            log.warn("⚠️ 绑定关系不存在：guardianId={}, elderId={}", guardianId, request.getElderId());
            return Result.error("您未绑定该长辈，无法代叫车");
        }

        // 2. 默认需要确认
        Boolean needConfirm = request.getNeedConfirm();
        if (needConfirm == null) {
            needConfirm = true;
        }

        // 3. 创建订单
        OrderInfo order = new OrderInfo();
        order.setOrderNo("AX" + System.currentTimeMillis() + new Random().nextInt(9000) + 1000);
        order.setUserId(request.getElderId());  // 实际乘车人
        order.setProxyUserId(guardianId);       // 代叫车人
        order.setElderUserId(request.getElderId());
        order.setStartLat(request.getStartLat());
        order.setStartLng(request.getStartLng());
        order.setDestLat(request.getDestLat());
        order.setDestLng(request.getDestLng());
        
        // ⭐ 处理 destAddress 为空的情况（使用坐标作为兜底）
        String destAddress = request.getDestAddress();
        if (destAddress == null || destAddress.trim().isEmpty()) {
            // 尝试使用坐标生成地址
            if (request.getDestLat() != null && request.getDestLng() != null) {
                destAddress = String.format("目的地(%.6f, %.6f)", request.getDestLat(), request.getDestLng());
            } else {
                destAddress = "未知目的地";
            }
            log.warn("⚠️ 前端未传入 destAddress，使用兜底值：{}", destAddress);
        }
        order.setDestAddress(destAddress);
        
        log.info("✅ 订单参数：destAddress={}, destLat={}, destLng={}", destAddress, request.getDestLat(), request.getDestLng());
        
        if (needConfirm) {
            // ⭐ 需要确认：状态为0-待确认
            order.setStatus(0);
        } else {
            // ⭐ 不需要确认：状态为1-已确认/待接单
            order.setStatus(1);
        }
        
        orderMapper.insert(order);

        // 4. 创建订单群聊（系统消息）
        OrderChat systemMsg = new OrderChat();
        systemMsg.setOrderId(order.getId());
        systemMsg.setSenderId(guardianId);
        systemMsg.setSenderType(2);  // 亲友
        systemMsg.setMessageType(1);  // 文字
        systemMsg.setContent("亲友" + guard.getGuardianName() + "为您代叫车辆，目的地：" + destAddress);
        orderChatMapper.insert(systemMsg);

        // 5. WebSocket推送给长辈（按前端文档要求的格式）
        Map<String, Object> pushData = new HashMap<>();
        if (needConfirm) {
            // ⭐ 推送确认请求 - 使用 ORDER_CREATED 类型
            pushData.put("type", "ORDER_CREATED");
            pushData.put("success", true);
            pushData.put("message", "您的亲友" + guard.getGuardianName() + "为您叫了一辆车");
            
            // data 字段包含完整订单信息
            Map<String, Object> orderData = new HashMap<>();
            orderData.put("orderId", order.getId());
            orderData.put("orderNo", order.getOrderNo());
            orderData.put("status", order.getStatus());
            orderData.put("userId", request.getElderId());
            orderData.put("guardianUserId", guardianId);
            orderData.put("destLat", request.getDestLat());
            orderData.put("destLng", request.getDestLng());
            orderData.put("poiName", destAddress);
            orderData.put("destAddress", destAddress);
            orderData.put("estimatePrice", 0.0);  // TODO: 后续补充价格预估
            orderData.put("createTime", LocalDateTime.now().toString());
            orderData.put("requesterName", guard.getGuardianName());
            orderData.put("destination", destAddress);
            
            pushData.put("data", orderData);
            
            // ⭐ 关键修复：确保使用订单中的 userId 推送，而不是请求参数
            Long targetUserId = order.getUserId();
            log.info("📤 准备推送代叫车通知：orderId={}, targetUserId={}, elderId_from_request={}", 
                order.getId(), targetUserId, request.getElderId());
            
            nativeWebSocket.sendMessageToUser(targetUserId, com.alibaba.fastjson.JSON.toJSONString(pushData));
            log.info("✅ 亲友{}发起代叫车请求（需确认），订单ID: {}, 推送给长辈userId={}", guardianId, order.getId(), targetUserId);
            
            // ⭐ 如果需要确认，不立即派单，等长辈确认后再派单
            if (!needConfirm) {
                // ⭐ 直接下单 - 触发司机分配
                try {
                    driverAssignmentService.assignDriverAndStartSimulation(
                        order.getId(), request.getElderId(), 
                        request.getStartLat(), request.getStartLng(),
                        request.getDestLat(), request.getDestLng()
                    );
                    log.info("✅ 已触发司机分配任务（代叫车-无需确认），orderId={}", order.getId());
                } catch (Exception e) {
                    log.error("❌ 触发司机分配失败，但订单已创建成功", e);
                }
            }
            
            return Result.success(order);  // ⭐ 返回订单对象
        } else {
            // ⭐ 直接下单 - 也使用 ORDER_CREATED 类型
            pushData.put("type", "ORDER_CREATED");
            pushData.put("success", true);
            pushData.put("message", "您的亲友" + guard.getGuardianName() + "为您叫了一辆车");
            
            Map<String, Object> orderData = new HashMap<>();
            orderData.put("orderId", order.getId());
            orderData.put("orderNo", order.getOrderNo());
            orderData.put("status", order.getStatus());
            orderData.put("userId", request.getElderId());
            orderData.put("guardianUserId", guardianId);
            orderData.put("destLat", request.getDestLat());
            orderData.put("destLng", request.getDestLng());
            orderData.put("poiName", destAddress);
            orderData.put("destAddress", destAddress);
            orderData.put("estimatePrice", 0.0);
            orderData.put("createTime", LocalDateTime.now().toString());
            orderData.put("requesterName", guard.getGuardianName());
            orderData.put("destination", destAddress);
            
            pushData.put("data", orderData);
            
            // ⭐ 关键修复：确保使用订单中的 userId 推送，而不是请求参数
            Long targetUserId = order.getUserId();
            log.info("📤 准备推送代叫车通知：orderId={}, targetUserId={}, elderId_from_request={}", 
                order.getId(), targetUserId, request.getElderId());
            
            nativeWebSocket.sendMessageToUser(targetUserId, com.alibaba.fastjson.JSON.toJSONString(pushData));
            log.info("✅ 亲友{}代叫车成功（无需确认），订单ID: {}, 推送给长辈userId={}", guardianId, order.getId(), targetUserId);
            
            // ⭐ 直接下单 - 触发司机分配
            try {
                driverAssignmentService.assignDriverAndStartSimulation(
                    order.getId(), request.getElderId(), 
                    request.getStartLat(), request.getStartLng(),
                    request.getDestLat(), request.getDestLng()
                );
                log.info("✅ 已触发司机分配任务（代叫车-无需确认），orderId={}", order.getId());
            } catch (Exception e) {
                log.error("❌ 触发司机分配失败，但订单已创建成功", e);
            }
            
            return Result.success(order);  // ⭐ 返回订单对象
        }
    }

    @Override
    @Transactional
    public Result<?> confirmProxyOrder(Long elderId, Long orderId, ConfirmProxyOrderRequest request) {
        log.info("👴 长辈{}确认代叫车订单{}, confirmed={}", elderId, orderId, request.getConfirmed());
        
        // 0. 参数校验
        if (request.getConfirmed() == null) {
            return Result.error("请指定是否确认（confirmed字段不能为空）");
        }
        
        // 1. 查询订单
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) {
            return Result.error("订单不存在");
        }
        
        // 2. 校验权限
        if (!order.getUserId().equals(elderId)) {
            return Result.error("无权操作此订单");
        }
        
        // 3. 校验状态
        if (order.getStatus() != 0) {
            return Result.error("订单状态不正确，只有待确认的订单才能操作");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        
        if (request.getConfirmed()) {
            // ⭐ 同意：更新订单状态为1-已确认/待接单
            order.setStatus(1);
            order.setConfirmTime(LocalDateTime.now());
            orderMapper.updateById(order);
            
            result.put("status", "CONFIRMED");
            
            // ⭐ 构建通知消息
            Map<String, Object> notifyData = new HashMap<>();
            notifyData.put("type", "PROXY_ORDER_CONFIRMED");
            notifyData.put("orderId", orderId);
            notifyData.put("elderUserId", elderId);
            notifyData.put("confirmed", true);
            notifyData.put("confirmTime", LocalDateTime.now().toString());
            String messageJson = com.alibaba.fastjson.JSON.toJSONString(notifyData);
            
            // ⭐ 通知代叫人（亲友）
            nativeWebSocket.sendMessageToUser(order.getProxyUserId(), messageJson);
            log.info("✅ 已向代叫人 proxyUserId={} 推送 PROXY_ORDER_CONFIRMED (同意)", order.getProxyUserId());
            
            // ⭐ 也通知长辈自己（确认成功反馈）
            nativeWebSocket.sendMessageToUser(elderId, messageJson);
            log.info("✅ 已向长辈 userId={} 推送 PROXY_ORDER_CONFIRMED (同意)", elderId);
            
            // ⭐ 长辈确认后，触发司机分配
            try {
                driverAssignmentService.assignDriverAndStartSimulation(
                    order.getId(), elderId, 
                    order.getStartLat(), order.getStartLng(),
                    order.getDestLat(), order.getDestLng()
                );
                log.info("✅ 已触发司机分配任务（代叫车-长辈确认），orderId={}", orderId);
            } catch (Exception e) {
                log.error("❌ 触发司机分配失败，但订单已确认成功", e);
            }
            
            log.info("✅ 长辈{}同意了代叫车请求，订单ID: {}", elderId, orderId);
            return Result.success(result);
            
        } else {
            // ⭐ 拒绝：更新订单状态为6-已拒绝
            order.setStatus(6);
            order.setRejectReason(request.getRejectReason());
            order.setConfirmTime(LocalDateTime.now());
            orderMapper.updateById(order);
            
            result.put("status", "REJECTED");
            
            // ⭐ 构建通知消息
            Map<String, Object> notifyData = new HashMap<>();
            notifyData.put("type", "PROXY_ORDER_CONFIRMED");
            notifyData.put("orderId", orderId);
            notifyData.put("elderUserId", elderId);
            notifyData.put("confirmed", false);
            notifyData.put("rejectReason", request.getRejectReason());
            notifyData.put("confirmTime", LocalDateTime.now().toString());
            String messageJson = com.alibaba.fastjson.JSON.toJSONString(notifyData);
            
            // ⭐ 通知代叫人（亲友）
            nativeWebSocket.sendMessageToUser(order.getProxyUserId(), messageJson);
            log.info("✅ 已向代叫人 proxyUserId={} 推送 PROXY_ORDER_CONFIRMED (拒绝)", order.getProxyUserId());
            
            // ⭐ 也通知长辈自己（确认成功反馈）
            nativeWebSocket.sendMessageToUser(elderId, messageJson);
            log.info("✅ 已向长辈 userId={} 推送 PROXY_ORDER_CONFIRMED (拒绝)", elderId);
            
            log.info("❌ 长辈{}拒绝了代叫车请求，订单ID: {}, 原因: {}", elderId, orderId, request.getRejectReason());
            return Result.success(result);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Result<?> getProxyOrders(Long guardianId) {
        List<OrderInfo> orders = orderMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OrderInfo>()
                .eq("proxy_user_id", guardianId)
                .orderByDesc("create_time")
        );
        return Result.success(orders);
    }

    @Override
    public Result<?> callDriver(Long orderId) {
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null || order.getDriverId() == null) {
            return Result.error("订单不存在或无司机");
        }

        // TODO: 待司机模块完成后，查询真实司机信息
        // Driver driver = driverMapper.selectById(order.getDriverId());
        Map<String, Object> data = new HashMap<>();
        data.put("driverPhone", "138****5678");  // 模拟数据
        data.put("driverName", "张*傅");  // 模拟数据
        
        return Result.success(data);
    }

    @Override
    public Result<?> callGuardian(Long elderId) {
        List<FamilyGuard> guardians = familyGuardMapper.selectActiveGuardsByElderId(elderId);
        if (guardians.isEmpty()) {
            return Result.error("暂无绑定的亲友");
        }

        // 返回第一个亲友
        FamilyGuard first = guardians.get(0);
        Map<String, Object> data = new HashMap<>();
        data.put("guardianPhone", first.getGuardianPhone());
        data.put("guardianName", first.getGuardianName());
        
        return Result.success(data);
    }
}
