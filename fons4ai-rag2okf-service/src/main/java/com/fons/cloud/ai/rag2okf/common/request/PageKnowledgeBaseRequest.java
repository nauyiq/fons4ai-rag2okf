package com.fons.cloud.ai.rag2okf.common.request;

import com.fons.cloud.common.request.BaseRequest;
import lombok.*;

/**
 * @author hongqy
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageKnowledgeBaseRequest extends BaseRequest {

    private int page;
    private int size;
    private Long workspaceId;

}
