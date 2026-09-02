package com.fons.cloud.ai.rag2okf.common.model.document;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/** 音频或视频内容的时间范围。 */
@Getter
@Setter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SourceTimeRange {
    /** 起始时间点，单位为毫秒，必须大于等于 0。 */ private long startMs;
    /** 结束时间点，单位为毫秒，必须大于起始时间点。 */ private long endMs;
}
