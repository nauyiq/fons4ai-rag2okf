package com.fons.cloud.ai.rag2okf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Rag2OKF 后端服务和异步任务的统一启动入口。
 *
 * @author hongqy
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class Rag2OkfApplication {

    /**
     * 启动 Rag2OKF Spring Boot 应用。
     *
     * @param args 进程启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(Rag2OkfApplication.class, args);
    }
}
