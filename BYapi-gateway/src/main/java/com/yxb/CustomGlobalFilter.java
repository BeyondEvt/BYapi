package com.yxb;

import com.yxb.byapiclientsdk.utils.SignUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;


// Order编排过滤器的优先级，先过滤哪个再过滤哪个
@Slf4j
@Component
/**
 * 全局过滤实现业务逻辑
 */
public class CustomGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> IP_WHITE_LIST = Arrays.asList("127.0.0.1");
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //1. 请求日志
        // 获取请求
        ServerHttpRequest request = exchange.getRequest();
        log.info("请求唯一标识: " + request.getId());
        log.info("请求路径: " + request.getPath().value());
        log.info("请求方法: " + request.getMethod());
        log.info("请求参数: " + request.getQueryParams());
        String sourceAddress = request.getLocalAddress().getHostString();
        log.info("请求来源地址: " + sourceAddress);
        log.info("请求来源地址: " + request.getRemoteAddress());

        ServerHttpResponse response = exchange.getResponse();
        //2. 访问控制 - 黑白名单
        if (!IP_WHITE_LIST.contains(sourceAddress)){
            return handleNoAuth(response);
        }

        //3. 用户鉴权（判断 ak、sk是否合法）
        // 获取请求头中的参数
        HttpHeaders headers = request.getHeaders();

        String accessKey = headers.getFirst("accessKey");
        //String secretKey = headers.getFirst("secretKey");
        String nonce = headers.getFirst("nonce");
        String timestamp = headers.getFirst("timestamp");
        String sign = headers.getFirst("sign");
        String body = headers.getFirst("body");
        // todo 实际情况应该是去数据库中查找是否已分配给用户
        if (!"yxbyxb".equals(accessKey)){
            return handleNoAuth(response);
        }
        assert nonce != null;
        if (Long.parseLong(nonce) > 10000L){
            return handleNoAuth(response);
        }
        // 时间和当前时间不能超过 5 分钟
        Long currentTime = System.currentTimeMillis() / 1000;
        final Long FIVE_MINUTES = 60 * 5L;
        if ((currentTime - Long.parseLong(timestamp)) > FIVE_MINUTES){
            return handleNoAuth(response);
        }

        // todo 实际情况应该是去数据库查出secretKey
        String serverSign = SignUtils.getSign(body, "abcdefg");
        if (!sign.equals(serverSign)) {
            return handleNoAuth(response);
        }

        //4. 请求的模拟接口是否存在
        // todo 从数据库中查询模拟接口是否存在，以及请求方法是否匹配（还可以校验请求参数）


        //t. **请求转发，调用模拟接口**
        Mono<Void> filter = chain.filter(exchange);

        //6. 响应日志
        log.info("响应: " + response.getStatusCode());

        //7. 调用成功，接口调用次数 +1
        // todo 调用成功后，接口调用次数+1 invokeCount
        if (response.getStatusCode() == HttpStatus.OK){

        } else {
            //8. 调用失败，返回一个规范的错误码
            handleInvokeError(response);
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }

    /**
     * 无权限
     * 通用拒绝请求
     * @param response
     * @return
     */
    public Mono<Void> handleNoAuth(ServerHttpResponse response){
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return response.setComplete();
    }

    /**
     * 调用失败
     *
     * @param response
     * @return
     */
    public Mono<Void> handleInvokeError(ServerHttpResponse response){
        response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        return response.setComplete();
    }
}