package com.fons.cloud.ai.rag2okf.domain.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUser;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbUserMapper;
import com.fons.cloud.ai.rag2okf.domain.service.KbUserDomainService;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * @author hongqy
 */
@Service
public class KbUserDomainServiceImpl extends ServiceImpl<KbUserMapper, KbUser> implements KbUserDomainService {

    @Override
    public KbUser findByEmail(String email) {
        return getOne(Wrappers.lambdaQuery(KbUser.class).eq(KbUser::getEmail, email).eq(KbUser::getDeleted, false));
    }

    @Override
    public void updateLastLoginAt(Long id) {
        update(Wrappers.<KbUser>lambdaUpdate()
                .eq(KbUser::getId, id)
                .set(KbUser::getLastLoginAt, new Date()));
    }
}
