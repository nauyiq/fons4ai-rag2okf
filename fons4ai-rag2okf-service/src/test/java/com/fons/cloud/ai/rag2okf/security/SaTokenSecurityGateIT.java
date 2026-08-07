package com.fons.cloud.ai.rag2okf.security;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.fons.cloud.auth.satoken.api.SaTokenAuthTemplate;
import com.fons.cloud.common.result.R;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sa-Token 安全门禁集成测试（T028）。
 *
 * <p>在最小 Spring Boot 上下文中验证安全门禁的端到端行为：
 * <ul>
 *   <li>全局登录校验拦截未认证请求</li>
 *   <li>白名单路径免登录（login、registration、error）</li>
 *   <li>仅 Authentication: Bearer 可恢复会话</li>
 *   <li>Cookie 传递被拒绝</li>
 *   <li>错误前缀被拒绝</li>
 *   <li>注销后会话失效</li>
 *   <li>统一错误响应不泄露内部信息</li>
 * </ul>
 *
 * @author hongqy
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = SaTokenSecurityGateIT.TestApplication.class,
        properties = {
                "spring.config.name=sa-token-security-gate",
                "sa-token.token-name=Authentication",
                "sa-token.token-prefix=Bearer",
                "sa-token.is-read-header=true",
                "sa-token.is-read-cookie=false",
                "sa-token.is-write-header=false",
                "sa-token.is-log=false",
                "sys.sa-token.global-login-check=true",
                "sys.sa-token.exclude-paths[0]=/__gate/login",
                "sys.sa-token.exclude-paths[1]=/__gate/register",
                "sys.sa-token.exclude-paths[2]=/error"
        }
)
@AutoConfigureMockMvc
class SaTokenSecurityGateIT {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 白名单路径免登录可访问。
     */
    @Test
    void shouldAllowAccessToExcludedPathsWithoutSession() throws Exception {
        mockMvc.perform(post("/__gate/login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/__gate/register"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * 未认证请求受保护路径返回 401 且统一响应不泄露内部信息。
     */
    @Test
    void shouldRejectUnauthenticatedAccessToProtectedPath() throws Exception {
        mockMvc.perform(get("/__gate/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("400001"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    /**
     * 仅 Authentication: Bearer 可恢复会话。
     */
    @Test
    void shouldOnlyAcceptAuthenticationBearerHeader() throws Exception {
        String token = obtainToken();

        mockMvc.perform(get("/__gate/protected").header("Authentication", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Cookie 传递 token 被拒绝。
     */
    @Test
    void shouldRejectCookieBasedToken() throws Exception {
        String token = obtainToken();

        mockMvc.perform(get("/__gate/protected").cookie(new Cookie("Authentication", token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("400001"));
    }

    /**
     * 错误前缀（Token 而非 Bearer）被拒绝。
     */
    @Test
    void shouldRejectWrongTokenPrefix() throws Exception {
        String token = obtainToken();

        mockMvc.perform(get("/__gate/protected").header("Authentication", "Token " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("400001"));
    }

    /**
     * 无前缀的裸 token 被拒绝。
     */
    @Test
    void shouldRejectBareTokenWithoutPrefix() throws Exception {
        String token = obtainToken();

        mockMvc.perform(get("/__gate/protected").header("Authentication", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("400001"));
    }

    /**
     * 注销后原 token 失效，后续请求返回 401。
     */
    @Test
    void shouldInvalidateSessionAfterLogout() throws Exception {
        String token = obtainToken();

        mockMvc.perform(get("/__gate/protected").header("Authentication", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/__gate/logout").header("Authentication", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/__gate/protected").header("Authentication", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("400001"));
    }

    /**
     * 伪造的 token 无法恢复会话。
     */
    @Test
    void shouldRejectForgedToken() throws Exception {
        mockMvc.perform(get("/__gate/protected").header("Authentication", "Bearer forged-invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("400001"));
    }

    private String obtainToken() throws Exception {
        return mockMvc.perform(post("/__gate/login"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\\\"token\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }

    /**
     * 最小 Web 容器，仅装配 Sa-Token、统一异常端点和测试接口。
     *
     * @author hongqy
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure.class,
            com.fons.cloud.db.autoconfigure.DatabaseConfiguration.class,
            com.fons.cloud.db.autoconfigure.MybatisPlusAutoConfiguration.class,
            com.fons.cloud.ai.agent.infrastructure.config.AgentAutoConfiguration.class,
            com.fons.cloud.ai.agent.langchain.config.LangChain4jAgentAutoConfiguration.class,
            com.fons.cloud.lock.config.DistributeLockAutoConfiguration.class,
            org.redisson.spring.starter.RedissonAutoConfigurationV2.class,
            com.fons.cloud.cache.config.IRedisAutoConfiguration.class,
            com.fons.cloud.cache.config.CacheAutoConfiguration.class
    })
    @ComponentScan(basePackages = {
            "com.fons.cloud.auth.satoken",
            "com.fons.cloud.ai.rag2okf.controller.endpoint"
    })
    static class TestApplication {

        /**
         * 使用内存会话存储隔离安全门禁测试。
         *
         * @return 测试专用 Sa-Token Dao
         */
        @Bean
        @Primary
        SaTokenDao saTokenDao() {
            return new SaTokenDaoDefaultImpl();
        }

        /**
         * 用于验证 Sa-Token 安全门禁的测试接口。
         *
         * @author hongqy
         */
        @RestController
        static class SecurityGateController {

            private final SaTokenAuthTemplate saTokenAuthTemplate;

            SecurityGateController(SaTokenAuthTemplate saTokenAuthTemplate) {
                this.saTokenAuthTemplate = saTokenAuthTemplate;
            }

            /**
             * 建立测试会话（白名单路径）。
             *
             * @return 包含临时 token 的统一响应
             */
            @PostMapping("/__gate/login")
            R<TokenResponse> login() {
                saTokenAuthTemplate.login("gate-test-user");
                return R.ok(new TokenResponse(saTokenAuthTemplate.getTokenValue()));
            }

            /**
             * 注册模拟端点（白名单路径）。
             *
             * @return 统一成功响应
             */
            @PostMapping("/__gate/register")
            R<Void> register() {
                return R.ok();
            }

            /**
             * 注销当前会话。
             *
             * @return 统一成功响应
             */
            @PostMapping("/__gate/logout")
            R<Void> logout() {
                saTokenAuthTemplate.logout();
                return R.ok();
            }

            /**
             * 受保护的测试接口。
             *
             * @return 已认证时的统一响应
             */
            @GetMapping("/__gate/protected")
            R<Void> protectedResource() {
                return R.ok();
            }
        }

        /**
         * 测试登录响应中的临时 token 载荷。
         *
         * @param token 仅用于本次测试请求链路的令牌
         * @author hongqy
         */
        record TokenResponse(String token) {
        }
    }
}
