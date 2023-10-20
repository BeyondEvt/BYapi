package com.yxb.byapiinterface.controller;
import com.yxb.byapiclientsdk.utils.SignUtils;
import com.yxb.byapiclientsdk.model.User;

import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;


/**
 * 查询名称 API
 *
 * @author yxb
 */

@RestController
@RequestMapping("/name")
public class NameController {

    @GetMapping("/get")
    public String getNameByGet(String name,HttpServletRequest request) {
        System.out.println(request.getHeader("yxbyxb"));
        return "(GET) 您的名字是" + name;
    }


    @PostMapping("/post")
    public String getNameByPost(@RequestParam String name) {
        return "(POST) 您的名字是" + name;
    }

    @PostMapping("/user")
    public String getUsernameByPost(@RequestBody User user, HttpServletRequest request) {
        String accessKey = request.getHeader("accessKey");
        String secretKey = request.getHeader("secretKey");
        String nonce = request.getHeader("nonce");
        String timestamp = request.getHeader("timestamp");
        String sign = request.getHeader("sign");
        String body = request.getHeader("body");
        // todo 实际情况应该是去数据库中查找是否已分配给用户
        if (!accessKey.equals("yxbyxb")){
            throw new RuntimeException("当前您无调用权限");
        }
        if (Long.parseLong(nonce) > 10000){
            throw new RuntimeException("当前您无调用权限");
        }
        // 时间和当前时间不能超过 5 分钟
//        if (timestamp){}
        // todo 实际情况应该是去数据库查出secretKey
        String serverSign = SignUtils.getSign(body, "abcdefg");
        if (!sign.equals(serverSign)) {
            throw new RuntimeException("当前您无调用权限");
        }
        String result = "(POST) 您的名字是" + user.getUsername();

        return result;
    }
}
