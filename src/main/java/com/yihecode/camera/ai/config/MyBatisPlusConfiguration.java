package com.yihecode.camera.ai.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * mybatis-plus配置，分页/全部删除等控制
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Configuration
public class MyBatisPlusConfiguration {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 自动分页
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInnerInterceptor.setMaxLimit(99L); // 设置每页最大值
        interceptor.addInnerInterceptor(paginationInnerInterceptor);

        // 防止全表更新与删除
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        // 多租户: TenantLineInnerInterceptor
        // 动态表名: DynamicTableNameInnerInterceptor
        // 乐观锁: OptimisticLockerInnerInterceptor
        // sql性能规范: IllegalSQLInnerInterceptor
        return interceptor;
    }

}
