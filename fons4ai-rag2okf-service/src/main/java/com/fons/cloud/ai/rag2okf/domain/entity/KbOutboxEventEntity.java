package com.fons.cloud.ai.rag2okf.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fons.cloud.db.entity.CommonEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.util.Date;

/**
 * 事务 Outbox 事件持久化实体。
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_outbox_event")
public class KbOutboxEventEntity extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件业务标识和投递幂等键。 */
    private String eventKey;

    /** 聚合类型。 */
    private String aggregateType;

    /** 聚合业务标识。 */
    private String aggregateKey;

    /** 事件类型。 */
    private String eventType;

    /** 事件载荷 JSON 快照。 */
    private String payloadJson;

    /** 投递状态。 */
    private String status;

    /** 已投递次数。 */
    private Integer attempt;

    /** 下次候选投递时间。 */
    private Date nextRunAt;

    /** 成功投递时间。 */
    private Date publishedAt;
}
