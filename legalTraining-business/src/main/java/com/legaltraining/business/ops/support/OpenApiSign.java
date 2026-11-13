package com.legaltraining.business.ops.support;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.legaltraining.common.utils.sign.Md5Utils;

/**
 * 开放接口签名（OP-03-01）
 *
 * 约定：signKey = MD5(appSecret)，服务端只存 signKey，不存明文密钥。
 * 签名串：appCode + "\n" + timestamp + "\n" + nonce + "\n" + requestUri
 * 算法：HmacSHA256(signKey, 签名串) 的小写十六进制。
 */
public class OpenApiSign
{
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private OpenApiSign()
    {
    }

    /** 生成明文密钥（只在创建/重置时返回一次） */
    public static String randomSecret()
    {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    /** 生成 AppID */
    public static String randomAppCode()
    {
        return "LT" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
    }

    /** 密钥摘要，即签名用的 signKey */
    public static String signKey(String secretPlain)
    {
        return Md5Utils.hash(secretPlain);
    }

    public static String sign(String signKey, String appCode, String timestamp, String nonce, String uri)
    {
        String data = appCode + "\n" + timestamp + "\n" + nonce + "\n" + uri;
        return hmacSha256(signKey, data);
    }

    public static String hmacSha256(String key, String data)
    {
        try
        {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes)
            {
                sb.append(HEX[(b >> 4) & 0x0F]).append(HEX[b & 0x0F]);
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            throw new IllegalStateException("签名计算失败", e);
        }
    }

    /** 定时比较，避免按字符短路带来的时序侧信道 */
    public static boolean safeEquals(String a, String b)
    {
        if (a == null || b == null)
        {
            return false;
        }
        if (a.length() != b.length())
        {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++)
        {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    /**
     * IP 白名单匹配：支持精确匹配与 IPv4 CIDR（如 10.0.0.0/24）
     */
    public static boolean ipMatches(String clientIp, String rule)
    {
        if (clientIp == null || rule == null || rule.trim().isEmpty())
        {
            return false;
        }
        String r = rule.trim();
        if (!r.contains("/"))
        {
            return r.equals(clientIp);
        }
        String[] parts = r.split("/");
        if (parts.length != 2)
        {
            return false;
        }
        try
        {
            long ip = ipv4ToLong(clientIp);
            long net = ipv4ToLong(parts[0]);
            int bits = Integer.parseInt(parts[1]);
            if (ip < 0 || net < 0 || bits < 0 || bits > 32)
            {
                return false;
            }
            long mask = bits == 0 ? 0L : (0xFFFFFFFFL << (32 - bits)) & 0xFFFFFFFFL;
            return (ip & mask) == (net & mask);
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static long ipv4ToLong(String ip)
    {
        String[] seg = ip.split("\\.");
        if (seg.length != 4)
        {
            return -1;
        }
        long value = 0;
        for (String s : seg)
        {
            int v = Integer.parseInt(s);
            if (v < 0 || v > 255)
            {
                return -1;
            }
            value = (value << 8) | v;
        }
        return value;
    }
}
