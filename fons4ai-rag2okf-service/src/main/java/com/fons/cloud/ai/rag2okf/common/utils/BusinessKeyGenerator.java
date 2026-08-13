package com.fons.cloud.ai.rag2okf.common.utils;

import java.security.SecureRandom;

/**
 * 使用时间和安全随机数生成 26 位 ULID 兼容业务主键。
 *
 * @author hongqy
 */
public class BusinessKeyGenerator {

    private static final char[] BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int RANDOM_BITS = 80;
    private final static SecureRandom SECURE_RANDOM = new SecureRandom();

    public static String nextKey() {
        char[] value = new char[26];
        long timestamp = System.currentTimeMillis();
        for (int index = 9; index >= 0; index--) {
            value[index] = BASE32[(int) (timestamp & 31)];
            timestamp >>>= 5;
        }
        byte[] random = new byte[RANDOM_BITS / Byte.SIZE];
        SECURE_RANDOM.nextBytes(random);
        int bitBuffer = 0;
        int bufferedBits = 0;
        int target = 10;
        for (byte randomByte : random) {
            bitBuffer = (bitBuffer << Byte.SIZE) | Byte.toUnsignedInt(randomByte);
            bufferedBits += Byte.SIZE;
            while (bufferedBits >= 5 && target < value.length) {
                bufferedBits -= 5;
                value[target++] = BASE32[(bitBuffer >>> bufferedBits) & 31];
            }
        }
        return new String(value);
    }
}
