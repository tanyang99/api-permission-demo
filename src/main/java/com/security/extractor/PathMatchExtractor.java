package com.security.extractor;

import com.security.enums.ExtractorType;
import com.security.enums.ParamSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Order(1)
@Component
@Slf4j
public class PathMatchExtractor implements ParameterExtractor {

    /**
     * 提取URL路径中的模板变量（如/rest/users/{userId}中的userId）
     *
     * @param request          HTTP请求对象
     * @param paramName        路径参数名称（如userId）
     * @param parseConfig      预留配置（路径提取暂不使用）
     * @param source           参数来源（必须为PATH）
     * @param useCachedRequest 是否使用缓存请求（路径参数无需缓存，此参数忽略）
     * @return 提取的参数值列表，无结果时返回空列表
     */
    @Override
    public List<String> extract(HttpServletRequest request, String paramName, String parseConfig,
                                ParamSource source, boolean useCachedRequest) {
        // 1. 校验参数来源是否为PATH（非PATH来源直接返回空）
        if (ParamSource.PATH != source) {
            log.trace("参数[{}]来源为[{}]，非路径参数，提取器不处理", paramName, source);
            return Collections.emptyList();
        }

        // 2. 校验参数名有效性（空参数名直接返回空）
        if (!StringUtils.hasText(paramName)) {
            log.error("路径参数名称为空，无法执行提取操作");
            return Collections.emptyList();
        }

        try {
            // 3. 获取Spring MVC解析的路径变量
            Object attrValue = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

            // 4. 处理路径变量为空的情况
            if (attrValue == null) {
                log.warn("当前请求URI中无模板变量，参数[{}]提取失败", paramName);
                return Collections.emptyList();
            }

            // 5. 校验路径变量是否为Map类型
            if (!(attrValue instanceof Map)) {
                log.warn("路径变量类型异常，参数[{}]提取失败。预期Map类型，实际为[{}]",
                        paramName, attrValue.getClass().getName());
                return Collections.emptyList();
            }

            // 6. 校验Map的键值是否均为String类型
            Map<?, ?> rawMap = (Map<?, ?>) attrValue;
            if (!isStringMap(rawMap)) {
                log.warn("路径变量包含非字符串类型的键或值，参数[{}]提取失败", paramName);
                return Collections.emptyList();
            }

            // 7. 安全转换并提取参数值
            @SuppressWarnings("unchecked")
            Map<String, String> uriVariables = (Map<String, String>) rawMap;
            String value = uriVariables.get(paramName);

            // 8. 根据提取结果返回对应值
            if (value != null) {
                log.info("路径参数[{}]提取成功，值:{}", paramName, value);
                return Collections.singletonList(value);
            } else {
                log.info("路径参数[{}]在URI模板变量中不存在", paramName);
                return Collections.emptyList();
            }

        } catch (Exception e) {
            log.error("路径参数[{}]提取过程发生异常", paramName, e);
            return Collections.emptyList();
        }
    }

    /**
     * 检查Map的键和值是否均为String类型
     */
    private boolean isStringMap(Map<?, ?> map) {
        // 空Map直接视为有效（避免空指针且符合逻辑）
        if (map.isEmpty()) {
            return true;
        }
        // 检查所有键和值是否为String类型
        return map.keySet().stream().allMatch(k -> k instanceof String)
                && map.values().stream().allMatch(v -> v instanceof String);
    }

    @Override
    public String supportParseMethod() {
        return ExtractorType.PATH_MATCH.name();
    }

    @Override
    public List<ParamSource> supportSources() {
        return Collections.singletonList(ParamSource.PATH);
    }
}
