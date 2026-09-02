package com.fons.cloud.ai.rag2okf.common.model.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.OcrPlanMode;

import java.util.List;
import java.util.Objects;

/**
 * 解析前生成的 OCR 范围计划。
 *
 * <p>页码使用从 1 开始的源页序。判定依据仅保存安全化代码，不保存文件正文、对象键、
 * 访问凭证或第三方响应。</p>
 *
 * @param mode OCR 执行范围
 * @param pageNumbers 需要 OCR 的源页序；{@link OcrPlanMode#NONE} 时为空
 * @param reasons 安全化判定依据
 * @author hongqy
 */
public record OcrPlan(OcrPlanMode mode, List<Integer> pageNumbers, List<String> reasons) {

    public OcrPlan {
        Objects.requireNonNull(mode, "mode must not be null");
        pageNumbers = pageNumbers == null ? List.of() : List.copyOf(pageNumbers);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        if (mode == OcrPlanMode.NONE && !pageNumbers.isEmpty()) {
            throw new IllegalArgumentException("NONE plan must not contain page numbers");
        }
        if (mode != OcrPlanMode.NONE && pageNumbers.isEmpty()) {
            throw new IllegalArgumentException("OCR plan must contain page numbers");
        }
        if (pageNumbers.stream().anyMatch(pageNumber -> pageNumber == null || pageNumber < 1)
                || pageNumbers.stream().distinct().count() != pageNumbers.size()) {
            throw new IllegalArgumentException("page numbers must be unique positive values");
        }
    }

    /**
     * 创建无需 OCR 的计划。
     *
     * @return 无 OCR 计划
     */
    public static OcrPlan none() {
        return new OcrPlan(OcrPlanMode.NONE, List.of(), List.of());
    }

    /**
     * 创建单页图片等整份内容必须 OCR 的计划。
     *
     * @param reason 安全化判定依据
     * @return 整份内容 OCR 计划
     */
    public static OcrPlan required(String reason) {
        return new OcrPlan(OcrPlanMode.REQUIRED, List.of(1), List.of(reason));
    }
}
