package com.yxb.byapiclientsdk.utils;

import cn.hutool.crypto.digest.DigestAlgorithm;
import cn.hutool.crypto.digest.Digester;

/**
 * 签名工具
 */
public class SignUtils {
    /**
     * 签名生成算法
     * @param body 参数哈希表
     * @param secretKey 密钥
     * @return 签名
     */
    public static String getSign(String body, String secretKey){
        Digester md5 = new Digester(DigestAlgorithm.SHA256);
        String content = body + "." + secretKey;

        return md5.digestHex(content);
    }

}
