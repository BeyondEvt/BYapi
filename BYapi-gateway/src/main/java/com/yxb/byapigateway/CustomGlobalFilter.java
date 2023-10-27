package com.yxb.byapigateway;

import com.yxb.byapiclientsdk.utils.SignUtils;
import com.yxb.byapicommon.model.entity.InterfaceInfo;
import com.yxb.byapicommon.model.entity.User;
import com.yxb.byapicommon.service.InnerInterfaceInfoService;
import com.yxb.byapicommon.service.InnerUserInterfaceInfoService;
import com.yxb.byapicommon.service.InnerUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


// Order编排过滤器的优先级，先过滤哪个再过滤哪个
@Slf4j
@Component
/**
 * 全局过滤实现业务逻辑
 */
public class CustomGlobalFilter implements GlobalFilter, Ordered {

    @DubboReference
    private InnerUserService innerUserService;
    @DubboReference
    private InnerInterfaceInfoService innerInterfaceInfoService;
    @DubboReference
    private InnerUserInterfaceInfoService innerUserInterfaceInfoService;

    private static final String INTERFACE_HOST = "http://localhost:8123";
    private static final List<String> IP_WHITE_LIST = Arrays.asList("127.0.0.1");
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //1. 请求日志
        // 获取请求
        ServerHttpRequest request = exchange.getRequest();

        String path = INTERFACE_HOST + request.getPath().value();
        String method = request.getMethod().toString();
        log.info("请求唯一标识: " + request.getId());
        log.info("请求路径: " +path);
        log.info("请求方法: " + method);
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
        User invokeUser = null;
        try {
            invokeUser = innerUserService.getInvokeUser(accessKey);
        } catch (Exception e){
            log.error("getInvokeUser error", e);
        }

        if (invokeUser == null) {
            return handleNoAuth(response);
        }

        //if (!"yxbyxb".equals(accessKey)){
        //    return handleNoAuth(response);
        //}
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

        // ]实际情况应该是去数据库查出secretKey
        String secretKey = invokeUser.getSecretKey();
        String serverSign = SignUtils.getSign(body, secretKey);
        if (sign == null || !sign.equals(serverSign)) {
            return handleNoAuth(response);
        }

        //4. 请求的模拟接口是否存在
        // 从数据库中查询模拟接口是否存在，以及请求方法是否匹配（还可以校验请求参数）
        InterfaceInfo interfaceInfo = null;
        try {
            interfaceInfo = innerInterfaceInfoService.getInterfaceInfo(path, method);
        } catch (Exception e){
            log.error("getInterfaceInfo error", e);
        }
        if (interfaceInfo == null) {
            return handleNoAuth(response);
        }



        //5. 请求转发，掉哟给模拟接口 + 响应日志
        //Mono<Void> filter = chain.filter(exchange);
        return handleResponse(exchange, chain, interfaceInfo.getId(), invokeUser.getId());

        ////7. 调用成功，接口调用次数 +1
        //// todo 调用成功后，接口调用次数+1 invokeCount
        //if (response.getStatusCode() == HttpStatus.OK){
        //
        //} else {
        //    //8. 调用失败，返回一个规范的错误码
        //    handleInvokeError(response);
        //}
        //
        //return chain.filter(exchange);
    }

    /**
     * 处理响应对象 ———— 处理方法等调用转发的接口后才会执行，保证了在接口调用后才处理响应对象，并进行调用次数统计等业务逻辑
     * @param exchange
     * @param chain
     * @return 处理后的响应对象
     */
    public Mono<Void> handleResponse(ServerWebExchange exchange, GatewayFilterChain chain, long interfaceId, long userId){
        try {
            ServerHttpResponse originalResponse = exchange.getResponse();
            // 从返回对象取出缓存工厂 -- 缓存数据
            DataBufferFactory bufferFactory = originalResponse.bufferFactory();
            // 拿到响应码
            HttpStatus statusCode = originalResponse.getStatusCode();

            if(statusCode == HttpStatus.OK){
                // 装饰response
                ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
                    // 该方法等调用转发的接口拿到返回值后才会执行，再进一步对响应结果进行处理
                    @Override
                    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                        log.info("body instanceof Flux: {}", (body instanceof Flux));
                        // 如果对象是反应式的
                        if (body instanceof Flux) {
                            // 拿到响应的body
                            Flux<? extends DataBuffer> fluxBody = Flux.from(body);
                            // 调用原本response的writeWith方法 --- 往返回值里写数据
                            return super.writeWith(
                                fluxBody.map(dataBuffer -> {
                                    // 在return响应对象结果前，进行调用次数统计等业务逻辑处理
                                    // 7. 调用成功后，接口调用次数+1 invokeCount
                                    try {
                                        innerUserInterfaceInfoService.invokeCount(interfaceId, userId);
                                    }catch (Exception e){
                                        log.error("invokeCount error" , e);
                                    }

                                    // 往缓存区拼接字符串，构建日志
                                    byte[] content = new byte[dataBuffer.readableByteCount()];
                                    dataBuffer.read(content);
                                    DataBufferUtils.release(dataBuffer);//释放掉内存
                                    // 构建日志
                                    StringBuilder sb2 = new StringBuilder(200);
                                    sb2.append("<--- {} {} \n");
                                    List<Object> rspArgs = new ArrayList<>();
                                    rspArgs.add(originalResponse.getStatusCode());
                                    //rspArgs.add(requestUrl);
                                    String data = new String(content, StandardCharsets.UTF_8);//data
                                    sb2.append(data);
                                    // 记录响应对象,打印日志
                                    log.info("响应结果: " + data);
                                    log.info(sb2.toString(), rspArgs.toArray());//log.info("<-- {} {}\n", originalResponse.getStatusCode(), data);
                                    return bufferFactory.wrap(content);
                                }));

                        } else {
                            //8.  调用失败，返回一个规范的错误码
                            log.error("<--- {} 响应code异常", getStatusCode());
                        }
                        return super.writeWith(body);
                    }
                };
                // ！！！设置 response 对象为装饰过的对象
                return chain.filter(exchange.mutate().response(decoratedResponse).build());
            }
            return chain.filter(exchange);//降级处理返回数据
        }catch (Exception e){
            log.error("网关处理响应异常\n" + e);
            return chain.filter(exchange);
        }

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