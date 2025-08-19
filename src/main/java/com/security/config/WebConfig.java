package com.security.config;

import com.security.filter.PermissionFilter;
import com.security.interceptor.PermissionInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private PermissionFilter permissionFilter;
    @Autowired
    private PermissionInterceptor permissionInterceptor;

    @Bean(name = "permissionFilterRegistrationBean")
    public FilterRegistrationBean<PermissionFilter> permissionFilterRegistration() {
        FilterRegistrationBean<PermissionFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(permissionFilter);
        bean.addUrlPatterns("/*"); // 仅对API路径生效
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE); // 优先执行
        return bean;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionInterceptor).addPathPatterns("/**") // 与Filter范围一致
                .order(Ordered.LOWEST_PRECEDENCE); // 晚于框架解析路径变量
    }
}
