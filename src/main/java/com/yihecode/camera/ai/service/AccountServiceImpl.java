package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yihecode.camera.ai.entity.Account;
import com.yihecode.camera.ai.mapper.AccountMapper;
import org.springframework.stereotype.Service;

/**
 * 账号管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Service
public class AccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements AccountService {

    /**
     * 根据账号查询
     *
     * @param account
     * @return
     */
    @Override
    public Account getByAccount(String account) {
        LambdaQueryWrapper<Account> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Account::getAccount, account);
        return this.getOne(queryWrapper, false);
    }
}