package com.fons.cloud.ai.rag2okf.domain.mapper.user;

import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspaceMember;
import com.fons.cloud.db.mybatisplus.BasePlusMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作空间成员关系 MyBatis-Plus Mapper。
 *
 * @author hongqy
 */
@Mapper
public interface KbWorkspaceMemberMapper extends BasePlusMapper<KbWorkspaceMember> {
}
