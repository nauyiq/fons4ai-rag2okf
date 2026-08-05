package com.fons.cloud.ai.rag2okf.domain.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 领域服务与 Java 文件作者注释的结构契约。
 *
 * @author hongqy
 */
class DomainServiceContractTest {

    private static final String ENTITY_PACKAGE =
            "com.fons.cloud.ai.rag2okf.domain.entity.";
    private static final String SERVICE_PACKAGE =
            "com.fons.cloud.ai.rag2okf.domain.service.";
    private static final String SERVICE_IMPL_PACKAGE =
            "com.fons.cloud.ai.rag2okf.domain.service.impl.";

    private static final List<String> ENTITY_NAMES = List.of(
            "KbUser",
            "KbWorkspace",
            "KbWorkspaceMember",
            "KbKnowledgeBase",
            "KbSourceDocument",
            "KbDocumentVersion",
            "KbParseRevision",
            "KbChunkRevision",
            "KbPublicationRevision",
            "KbProcessingTask",
            "KbOutboxEvent",
            "KbModelConnection",
            "KbModelProfile",
            "KbModelBinding"
        );

    @Test
    void everyEntityShouldHaveMatchingDomainService() throws Exception {
        for (String name : ENTITY_NAMES) {
            Class<?> entityType = Class.forName(ENTITY_PACKAGE + name + "Entity");
            Class<?> serviceType = Class.forName(SERVICE_PACKAGE + name + "DomainService");
            Class<?> serviceImplType = Class.forName(
                    SERVICE_IMPL_PACKAGE + name + "DomainServiceImpl"
            );

            assertThat(serviceType).isAssignableTo(IService.class);
            assertThat(serviceImplType).isAssignableTo(serviceType);
            assertThat(serviceImplType).isAssignableTo(ServiceImpl.class);
            assertThat(serviceImplType).hasAnnotation(Service.class);
            assertThat(resolveServiceEntityType(serviceType)).isEqualTo(entityType);
            assertThat(resolveServiceImplEntityType(serviceImplType)).isEqualTo(entityType);
        }
    }

    @Test
    void everyJavaSourceShouldDeclareAuthor() throws IOException {
        List<Path> javaSources;
        try (Stream<Path> paths = Stream.concat(
                Files.walk(Path.of("src", "main", "java")),
                Files.walk(Path.of("src", "test", "java"))
        )) {
            javaSources = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }

        assertThat(javaSources)
                .isNotEmpty()
                .allSatisfy(path -> assertThat(readSource(path))
                        .as("Java 文件 %s 应声明作者", path)
                        .contains("@author hongqy"));
    }

    private Type resolveServiceEntityType(Class<?> serviceType) {
        ParameterizedType serviceContract = (ParameterizedType)
                serviceType.getGenericInterfaces()[0];
        return serviceContract.getActualTypeArguments()[0];
    }

    private Type resolveServiceImplEntityType(Class<?> serviceImplType) {
        ParameterizedType serviceBase = (ParameterizedType)
                serviceImplType.getGenericSuperclass();
        return serviceBase.getActualTypeArguments()[1];
    }

    private String readSource(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
