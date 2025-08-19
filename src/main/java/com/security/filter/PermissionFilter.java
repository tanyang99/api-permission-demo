package com.security.filter;

import com.security.context.PermissionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class PermissionFilter implements Filter {

    // 使用Spring自带的HttpMethod枚举定义需要读取请求体的方法
    private static final List<HttpMethod> METHODS_NEEDING_BODY = Arrays.asList(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    // 文件上传的Content-Type前缀
    private static final String MULTIPART_CONTENT_TYPE_PREFIX = "multipart/";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String requestUri = httpRequest.getRequestURI();
        HttpServletRequest wrappedRequest = httpRequest;

        // 解析请求方法为Spring HttpMethod枚举（自动处理大小写和非法值）
        HttpMethod httpMethod = HttpMethod.resolve(httpRequest.getMethod());

        // 初始化上下文
        PermissionContext.init();
        PermissionContext.getContextData().setUri(requestUri);

        try {
            // 判断是否需要缓存请求体（基于HttpMethod枚举）
            if (shouldCacheRequestBody(httpRequest, httpMethod)) {
                wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
                PermissionContext.getContextData().setUseCachedRequest(true);
            }
            // 执行过滤器链
            chain.doFilter(wrappedRequest, response);
        } catch (Exception e) {
            log.error("请求 [{} {}] 处理异常", httpMethod, requestUri, e);
            throw e;
        } finally {
            PermissionContext.clear();
        }
    }

    /**
     * 判断是否需要缓存请求体IO流（基于Spring HttpMethod枚举）
     */
    private boolean shouldCacheRequestBody(HttpServletRequest request, HttpMethod httpMethod) {
        // 1. 排除无法识别的HTTP方法
        if (httpMethod == null) {
            return false;
        }

        // 2. 检查是否是需要请求体的方法（使用枚举集合判断）
        if (!METHODS_NEEDING_BODY.contains(httpMethod)) {
            return false;
        }

        // 3. 排除文件上传请求
        String contentType = request.getContentType();
        if (contentType != null && contentType.startsWith(MULTIPART_CONTENT_TYPE_PREFIX)) {
            log.info("请求 [{} {}] 是文件上传，不缓存IO流", httpMethod, request.getRequestURI());
            return false;
        }

        return true;
    }
}
