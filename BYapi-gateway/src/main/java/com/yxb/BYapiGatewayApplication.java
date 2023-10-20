package com.yxb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BYapiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(BYapiGatewayApplication.class, args);
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("to_baidu", r -> r.path("/baidu")
                        .uri("https://www.baidu.com"))
                .route("to_taobao", r -> r.path("/taobao")
                        .uri("https://taobao.com"))

                .build();
    }

}
