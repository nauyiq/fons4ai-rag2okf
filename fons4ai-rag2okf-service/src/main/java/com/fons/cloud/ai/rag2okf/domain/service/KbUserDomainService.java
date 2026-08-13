package com.fons.cloud.ai.rag2okf.domain.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUser;

import java.util.Date;

/**
 * @author hongqy
 */
public interface KbUserDomainService extends IService<KbUser> {

    /**
     * 根据邮箱查找用户
     * @param email 邮箱
     * @return 用户
     */
    KbUser findByEmail(String email);

    /**
     * 更新用户最后登录时间
     * @param id 用户ID
     */
    void updateLastLoginAt(Long id);
}
