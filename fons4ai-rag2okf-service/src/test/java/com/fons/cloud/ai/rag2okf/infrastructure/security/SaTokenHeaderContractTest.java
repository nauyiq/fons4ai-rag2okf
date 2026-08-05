package com.fons.cloud.ai.rag2okf.infrastructure.security;

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
 * Sa-Token Header Bearer 传递方式的 Web 契约测试。
 *
 * <p>测试使用内存 Sa-Token Dao，不访问 Redis、MySQL 或其他外部环境。</p>
 *
 * @author hongqy
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = SaTokenHeaderContractTest.TestApplication.class,
        properties = {
                "spring.config.name=sa-token-header-contract",
                "sa-token.token-name=Authentication",
                "sa-token.token-prefix=Bearer",
                "sa-token.is-read-header=true",
                "sa-token.is-read-cookie=false",
                "sa-token.is-write-header=false",
                "sys.sa-token.global-login-check=true",
                "sys.sa-token.exclude-paths[0]=/__contract/login",
                "sys.sa-token.exclude-paths[1]=/error"
        }
)
@AutoConfigureMockMvc
class SaTokenHeaderContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldOnlyRestoreTheSessionFromAuthenticationBearerHeader() throws Exception {
        String token = mockMvc.perform(post("/__contract/login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\\\"token\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(get("/__contract/current").header("Authentication", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/__contract/current").cookie(new Cookie("Authentication", token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("400001"));

        mockMvc.perform(get("/__contract/current").header("Authentication", "Token " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("400001"));

        mockMvc.perform(get("/__contract/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("400001"));
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
         * 使用内存会话存储隔离 Header 契约测试。
         *
         * @return 测试专用 Sa-Token Dao
         */
        @Bean
        @Primary
        SaTokenDao saTokenDao() {
            return new SaTokenDaoDefaultImpl();
        }

        /**
         * 用于验证 Sa-Token 请求恢复行为的最小受保护接口。
         *
         * @author hongqy
         */
        @RestController
        static class HeaderContractController {

            private final SaTokenAuthTemplate saTokenAuthTemplate;

            HeaderContractController(SaTokenAuthTemplate saTokenAuthTemplate) {
                this.saTokenAuthTemplate = saTokenAuthTemplate;
            }

            /**
             * 建立仅用于测试的会话。
             *
             * @return 包含临时 token 的统一响应
             */
            @PostMapping("/__contract/login")
            R<TokenResponse> login() {
                saTokenAuthTemplate.login("contract-user");
                return R.ok(new TokenResponse(saTokenAuthTemplate.getTokenValue()));
            }

            /**
             * 验证全局拦截器是否从 Header 恢复会话。
             *
             * @return 已认证时的统一响应
             */
            @GetMapping("/__contract/current")
            R<Void> current() {
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
