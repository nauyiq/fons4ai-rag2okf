package com.fons.cloud.ai.rag2okf.domain.user;

import com.fons.cloud.ai.rag2okf.common.constants.UserStatus;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceMemberStatus;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceRole;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceStatus;
import com.fons.cloud.ai.rag2okf.common.constants.WorkspaceType;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbWorkspaceMemberEntity;
import com.baomidou.mybatisplus.annotation.EnumValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户与工作空间状态枚举映射测试。
 *
 * @author hongqy
 */
class IdentityStatusEnumTest {

    @Test
    void shouldUseTypedEnumsForIdentityAndWorkspacePersistenceFields() throws NoSuchFieldException {
        assertThat(KbUserEntity.class.getDeclaredField("status").getType()).isEqualTo(UserStatus.class);
        assertThat(KbWorkspaceEntity.class.getDeclaredField("workspaceType").getType()).isEqualTo(WorkspaceType.class);
        assertThat(KbWorkspaceEntity.class.getDeclaredField("status").getType()).isEqualTo(WorkspaceStatus.class);
        assertThat(KbWorkspaceMemberEntity.class.getDeclaredField("localRole").getType()).isEqualTo(WorkspaceRole.class);
        assertThat(KbWorkspaceMemberEntity.class.getDeclaredField("status").getType()).isEqualTo(WorkspaceMemberStatus.class);
        assertThat(UserStatus.class.getDeclaredField("value").isAnnotationPresent(EnumValue.class)).isTrue();
        assertThat(WorkspaceStatus.class.getDeclaredField("value").isAnnotationPresent(EnumValue.class)).isTrue();
        assertThat(WorkspaceMemberStatus.class.getDeclaredField("value").isAnnotationPresent(EnumValue.class)).isTrue();
        assertThat(WorkspaceType.class.getDeclaredField("value").isAnnotationPresent(EnumValue.class)).isTrue();
        assertThat(WorkspaceRole.class.getDeclaredField("value").isAnnotationPresent(EnumValue.class)).isTrue();
    }
}
