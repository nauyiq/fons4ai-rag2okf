package com.fons.cloud.ai.rag2okf.common.constants.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WorkspaceRole} 的显式角色覆盖规则测试。
 */
class WorkspaceRoleTest {

    @Test
    void adminShouldCoverAllExistingRoles() {
        assertTrue(WorkspaceRole.ADMIN.covers(WorkspaceRole.ADMIN));
        assertTrue(WorkspaceRole.ADMIN.covers(WorkspaceRole.KNOWLEDGE_USER));
    }

    @Test
    void knowledgeUserShouldOnlyCoverItself() {
        assertTrue(WorkspaceRole.KNOWLEDGE_USER.covers(WorkspaceRole.KNOWLEDGE_USER));
        assertFalse(WorkspaceRole.KNOWLEDGE_USER.covers(WorkspaceRole.ADMIN));
    }

    @Test
    void nullRequiredRoleShouldBeDenied() {
        assertFalse(WorkspaceRole.ADMIN.covers(null));
        assertFalse(WorkspaceRole.KNOWLEDGE_USER.covers(null));
    }
}
