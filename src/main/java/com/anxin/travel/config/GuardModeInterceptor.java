package com.anxin.travel.config;

import com.anxin.travel.common.util.RedisUtil;
import com.anxin.travel.common.util.UserContext;
import com.anxin.travel.module.user.entity.User;
import com.anxin.travel.module.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

/**
 * 长辈模式权限拦截器
 * 功能:当用户处于长辈精简模式时,只允许访问白名单内的接口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuardModeInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;
    private final RedisUtil redisUtil;

    /**
     * 长辈模式白名单路径
     * 长辈模式用户只能访问这些路径
     */
    private static final List<String> ALLOWED_PATHS = Arrays.asList(
        // ========== 认证相关(切换账号必须) ==========
        "/api/auth/logout",             // 退出登录
        "/api/auth/login",              // 登录(切换账号时需要)
        "/api/auth/code",               // 获取验证码
        "/api/auth/register",           // 注册
        "/api/auth/forgot-password",    // 找回密码
        
        // ========== 用户基础信息 ==========
        "/api/user/profile",            // 获取用户信息(包含guardMode字段)
        "/api/user/avatar",             // 头像上传/获取
        "/api/user/emergency",          // 紧急联系人列表
        
        // ========== 订单相关 ==========
        "/api/order/current",           // 查看当前订单
        "/api/order/detail",            // 订单详情
        "/api/order/list",              // 订单列表
        "/api/order/",                  // 订单详情（/api/order/{id}）
        
        // ========== 亲情守护相关 ==========
        "/api/guard/myGuardians",       // 查看亲友列表
        "/api/guard/unbindAll",         // 解绑全部
        "/api/guard/callDriver",        // 呼叫司机
        "/api/guard/callGuardian",      // 呼叫亲友
        "/api/guard/confirmProxyOrder", // 长辈确认代叫车请求（关键）
        
        // ========== AI助手相关(长辈也需要地图和搜索) ==========
        "/api/agent/location",          // 位置上报(用于地图定位和AI推荐)
        "/api/agent/search",            // POI搜索(长辈也需要搜索地点)
        "/api/agent/image",             // 图片识别(长辈拍照识别地点)
        "/api/agent/cleanup",           // 清理会话
        // 注意: /api/agent/confirm 不在白名单中,长辈不能通过智能体确认订单
        
        // ========== 订单群聊相关(路径前缀匹配) ==========
        "/api/chat/order",              // 订单群聊(包含history/send等子路径)
        "/api/chat/voiceToText",        // 语音转文字
        "/api/chat/textToSpeech",       // 文字转语音
        
        // ========== 私聊相关(亲情守护真人聊天) ==========
        "/api/chat/private"             // 私聊(包含history/send/read/unread等子路径)
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return true;  // 未登录,由AuthInterceptor处理
        }

        // 【性能优化】先从 Redis 缓存获取 guard_mode，避免每次请求都查数据库
        String cacheKey = "user:guard_mode:" + userId;
        Integer guardMode = null;
        try {
            Object cachedValue = redisUtil.get(cacheKey);
            if (cachedValue != null) {
                guardMode = Integer.valueOf(cachedValue.toString());
                log.debug("✅ 从缓存获取 guard_mode：userId={}, guardMode={}", userId, guardMode);
            }
        } catch (Exception e) {
            log.warn("⚠️ 读取缓存失败，将查询数据库：{}", e.getMessage());
        }
        
        // 缓存未命中，查询数据库
        if (guardMode == null) {
            User user = userMapper.selectById(userId);
            if (user == null) {
                return true;  // 用户不存在，放行
            }
            guardMode = user.getGuardMode() != null ? user.getGuardMode() : 0;
            
            // 写入缓存(5分钟过期)
            try {
                redisUtil.set(cacheKey, String.valueOf(guardMode), 300);
                log.debug("💾 已缓存 guard_mode:userId={}, guardMode={}", userId, guardMode);
            } catch (Exception e) {
                log.warn("⚠️ 写入缓存失败:{}", e.getMessage());
            }
        }
        
        // 普通用户或未设置模式，直接放行
        if (guardMode != 1) {
            return true;
        }

        // 长辈模式:白名单校验
        String uri = request.getRequestURI();
        
        for (String path : ALLOWED_PATHS) {
            if (uri.startsWith(path)) {
                log.debug("✅ 长辈模式用户{}访问白名单路径:{}", userId, uri);
                return true;
            }
        }

        // 不在白名单,拒绝访问
        log.warn("⚠️ 长辈模式用户{}尝试访问受限路径:{}", userId, uri);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"长辈模式无法访问该功能\"}");
        return false;
    }
}
