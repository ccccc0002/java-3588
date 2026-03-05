package com.yihecode.camera.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yihecode.camera.ai.entity.Account;

/**
 * 账号管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public interface AccountService extends IService<Account> {

    /**
     * 根据账号查询
     * @param account
     * @return
     */
    Account getByAccount(String account);
}