package com.yxb.byapiclientsdk.client;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.yxb.byapiclientsdk.model.User;


import java.util.HashMap;
import java.util.Map;

import static com.yxb.byapiclientsdk.utils.SignUtils.getSign;


/**
 * 调用第三方接口的客户端
 *
 * @author yxb
 */
public class BYapiClient {
    // 网关地址
    private static final String GATE_WAY = "http://localhost:8090";
    // API 签名认证
    private String accessKey;
    private String secretKey;


    public BYapiClient(String accessKey, String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    public String getNameByGet(String name) {
        //可以单独传入http参数，这样参数会自动做URL编码，拼接在URL中
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("name", name);

        String result = HttpUtil.get(GATE_WAY + "/api/name/", paramMap);
        System.out.println(result);
        return result;
    }


    public String getNameByPost(String name) {
        //可以单独传入http参数，这样参数会自动做URL编码，拼接在URL中
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("name", name);

        String result = HttpUtil.post(GATE_WAY + "/api/name/", paramMap);
        System.out.println(result);
        return result;
    }


    private Map<String, String> getHeaderMap(String body){
        Map<String,String> hashmap = new HashMap<>();
        hashmap.put("accessKey", accessKey);
        // 一定不能直接发送给后端
        //hashmap.put("secretKey", secretKey);
        hashmap.put("nonce", RandomUtil.randomNumbers(4));
        hashmap.put("body", body); // 用户传递的参数
        hashmap.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        hashmap.put("sign", getSign(body, secretKey));

        return hashmap;
    }




    public String getUsernameByPost(User user) {
        String json = JSONUtil.toJsonStr(user);
        HttpResponse httpResponse = HttpRequest.post(GATE_WAY + "/api/name/user")
                .addHeaders(getHeaderMap(json))
                .body(json)
                .execute();
        System.out.println(httpResponse.getStatus());
        String result = httpResponse.body();
        System.out.println(result);
        return  result;
    }
}
