package com.yxb.byapiclientsdk;

import com.yxb.byapiclientsdk.client.BYapiClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("byapi.client")
@Data
@ComponentScan
public class BYApiClientConfig {

    private String accessKey;
    private String secretKey;

    @Bean
    public BYapiClient bYapiClient(){
        return new BYapiClient(accessKey, secretKey);
    }
}
