package com.fons.cloud.ai.rag2okf.infrastructure.document.chunking.strategy.support;

import com.fons.cloud.ai.rag2okf.common.model.document.ChunkManifestItem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 分块清单内部使用的确定性标识和摘要计算。 */
public final class ChunkManifestSupport {

    private ChunkManifestSupport() {
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    public static String chunkId(String resultKey, int ordinal, String role, String content) {
        return "chk_" + hash(resultKey + '\n' + ordinal + '\n' + role + '\n' + content).substring(0, 24);
    }

    public static String manifestContent(ChunkManifestItem item) {
        return item.getChunkId() + '\n' + item.getOrdinal() + '\n' + item.getRole() + '\n'
                + (item.getParentChunkId() == null ? "" : item.getParentChunkId()) + '\n'
                + item.getContentHash() + '\n' + String.join(",", item.getSourceBlockIds());
    }
}
