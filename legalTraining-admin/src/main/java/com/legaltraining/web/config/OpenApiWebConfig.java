package com.legaltraining.web.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.legaltraining.web.interceptor.OpenApiInterceptor;

/**
 * 开放接口拦截器注册
 *
 * 只挂 /openapi/v1/**，内部接口（/edu /exh /ops /system）不受影响。
 */
@Configuration
public class OpenApiWebConfig implements WebMvcConfigurer
{
    @Autowired
    private OpenApiInterceptor openApiInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        registry.addInterceptor(openApiInterceptor).addPathPatterns("/openapi/v1/**");
    }
}
