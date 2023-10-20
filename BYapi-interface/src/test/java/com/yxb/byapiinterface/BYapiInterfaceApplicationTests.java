package com.yxb.byapiinterface;

import com.yxb.byapiclientsdk.client.BYapiClient;
import com.yxb.byapiclientsdk.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class BYapiInterfaceApplicationTests {

    @Resource
    private BYapiClient bYapiClient;
    @Test
    void contextLoads() {
        String name = bYapiClient.getNameByGet("yxbyxb");
        User user = new User();
        user.setUsername("beyond");
        String usernameByPost = bYapiClient.getUsernameByPost(user);

        System.out.println(name);
        System.out.println(usernameByPost);
    }

}
