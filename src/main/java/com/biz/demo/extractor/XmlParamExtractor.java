package com.biz.demo.extractor;

import com.security.enums.ParamSource;
import com.security.extractor.ParameterExtractor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.servlet.http.HttpServletRequest;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * 从XML请求体提取参数的自定义提取器（解析方式：CUSTOM）
 */
@Component  // 必须标注@Component，确保被ExtractorFactory扫描并注册
public class XmlParamExtractor implements ParameterExtractor {

    public static final String PARSE_METHOD = "XML_PATH";

    /**
     * 核心提取逻辑
     *
     * @param request          请求对象
     * @param paramName        参数名（仅用于日志）
     * @param parseConfig      解析配置（XML XPath表达式）
     * @param source           参数来源
     * @param useCachedRequest 是否使用缓存的请求体
     * @return 提取的参数值列表
     */
    @Override
    public List<String> extract(HttpServletRequest request, String paramName, String parseConfig, ParamSource source, boolean useCachedRequest) {
        // 1. 仅处理BODY来源的参数
        if (ParamSource.BODY != source) {
            return Collections.emptyList();
        }

        // 2. 确保使用缓存的请求体（XML在请求体中，需重复读取）
        if (!useCachedRequest || !(request instanceof ContentCachingRequestWrapper)) {
            return Collections.emptyList();
        }

        // 3. 验证XPath表达式配置
        if (!StringUtils.hasText(parseConfig)) {
            return Collections.emptyList();
        }

        try {
            // 4. 从缓存请求中读取XML内容
            ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
            byte[] contentBytes = wrapper.getContentAsByteArray();
            if (contentBytes.length == 0) {
                return Collections.emptyList();
            }
            String xmlBody = new String(contentBytes, StandardCharsets.UTF_8);

            // 5. 使用XPath解析XML
            Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xmlBody)));
            XPath xpath = XPathFactory.newInstance().newXPath();
            String result = xpath.evaluate(parseConfig, doc);  // 执行XPath表达式

            // 6. 处理结果（单值转列表）
            return result != null ? Collections.singletonList(result) : Collections.emptyList();
        } catch (Exception e) {
            // 提取失败返回空列表（可根据业务需求抛出异常）
            return Collections.emptyList();
        }
    }

    /**
     * 自定义名称解析方式。
     */
    @Override
    public String supportParseMethod() {
        return PARSE_METHOD;
    }

    /**
     * 支持的参数来源（此提取器仅支持BODY）
     */
    @Override
    public List<ParamSource> supportSources() {
        return Collections.singletonList(ParamSource.BODY);
    }
}