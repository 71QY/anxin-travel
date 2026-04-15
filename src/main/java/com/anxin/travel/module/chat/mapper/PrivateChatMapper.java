package com.anxin.travel.module.chat.mapper;

import com.anxin.travel.module.chat.entity.PrivateChat;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface PrivateChatMapper extends BaseMapper<PrivateChat> {
    
    /**
     * 查询两人之间的聊天记录（按时间排序）
     */
    @Select("SELECT * FROM private_chat " +
            "WHERE (sender_id = #{userId1} AND receiver_id = #{userId2}) " +
            "   OR (sender_id = #{userId2} AND receiver_id = #{userId1}) " +
            "ORDER BY created_at ASC")
    List<PrivateChat> selectChatHistory(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
    
    /**
     * 标记消息为已读
     */
    @Update("UPDATE private_chat SET is_read = 1 WHERE receiver_id = #{receiverId} AND sender_id = #{senderId} AND is_read = 0")
    int markAsRead(@Param("receiverId") Long receiverId, @Param("senderId") Long senderId);
    
    /**
     * 查询未读消息数量
     */
    @Select("SELECT COUNT(*) FROM private_chat WHERE receiver_id = #{receiverId} AND is_read = 0")
    int countUnreadMessages(@Param("receiverId") Long receiverId);
}
