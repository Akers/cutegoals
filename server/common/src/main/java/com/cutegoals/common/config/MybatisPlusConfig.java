package com.cutegoals.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 * <p>
 * 注册分页拦截器 PaginationInnerInterceptor，确保 BaseMapper#selectPage
 * 正确执行 count 查询并填充 total 字段。
 * 缺少此拦截器时，selectPage 仅执行数据查询，total 保持默认值 0，
 * 导致返回 totalElements=0 但 content 有数据的分页 bug。
 * </p>
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
