package com.fons.cloud.ai.rag2okf.parsing;

import com.fons.cloud.ai.rag2okf.domain.parsing.SourceAnchor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SourceAnchor 值对象测试。
 *
 * <p>覆盖 AC-014：不伪造页码，locatorType=NONE 时 page 和 blockIndex 为 null。
 *
 * @author hongqy
 */
@DisplayName("SourceAnchor 来源定位")
class SourceAnchorTest {

    @Test
    @DisplayName("none() 创建无来源定位，page 和 blockIndex 为 null")
    void none_createsNoneAnchorWithNullFields() {
        SourceAnchor anchor = SourceAnchor.none();

        assertEquals(SourceAnchor.NONE, anchor.locatorType());
        assertNull(anchor.page(), "NONE 时 page 必须为 null");
        assertNull(anchor.blockIndex(), "NONE 时 blockIndex 必须为 null");
    }

    @Test
    @DisplayName("page() 创建页码定位，blockIndex 为 null")
    void page_createsPageAnchorWithPageNumber() {
        SourceAnchor anchor = SourceAnchor.page(1);

        assertEquals(SourceAnchor.PAGE, anchor.locatorType());
        assertEquals(1, anchor.page());
        assertNull(anchor.blockIndex(), "PAGE 时 blockIndex 为 null");
    }

    @Test
    @DisplayName("block() 创建块序号定位，page 为 null")
    void block_createsBlockAnchorWithBlockIndex() {
        SourceAnchor anchor = SourceAnchor.block(0);

        assertEquals(SourceAnchor.BLOCK, anchor.locatorType());
        assertEquals(0, anchor.blockIndex());
        assertNull(anchor.page(), "BLOCK 时 page 为 null");
    }

    @Test
    @DisplayName("不同定位类型的 page 和 blockIndex 不交叉")
    void differentTypesDoNotCrossFields() {
        SourceAnchor pageAnchor = SourceAnchor.page(5);
        SourceAnchor blockAnchor = SourceAnchor.block(10);
        SourceAnchor noneAnchor = SourceAnchor.none();

        // PAGE 只有 page，没有 blockIndex
        assertNotNull(pageAnchor.page());
        assertNull(pageAnchor.blockIndex());

        // BLOCK 只有 blockIndex，没有 page
        assertNull(blockAnchor.page());
        assertNotNull(blockAnchor.blockIndex());

        // NONE 都没有
        assertNull(noneAnchor.page());
        assertNull(noneAnchor.blockIndex());
    }
}
