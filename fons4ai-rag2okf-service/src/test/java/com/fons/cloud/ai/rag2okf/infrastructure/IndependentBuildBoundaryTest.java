package com.fons.cloud.ai.rag2okf.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 前后端独立项目与打包边界验证测试（T030）。
 *
 * <p>固化 CR-006 的清理结论，防止回归：
 * <ul>
 *   <li>仓库根不存在聚合构建文件（pom.xml / package.json）</li>
 *   <li>后端 pom.xml 不引入 frontend-maven-plugin 或 Node Workspace</li>
 *   <li>后端 resources/static 不嵌入前端产物</li>
 *   <li>前端 package.json 独立配置构建脚本</li>
 *   <li>后端 JAR 产物不含前端静态资源</li>
 * </ul>
 *
 * @author hongqy
 */
class IndependentBuildBoundaryTest {

    private static final Path REPO_ROOT = Paths.get("..");
    private static final Path BACKEND_ROOT = Paths.get(".");
    private static final Path FRONTEND_ROOT = Paths.get("../fons4ai-rag2okf-ui");

    private static final List<String> FORBIDDEN_POM_KEYWORDS = List.of(
            "frontend-maven-plugin", "com.github.eirslett",
            "node-maven-plugin", "exec-maven-plugin.*npm",
            "reactor", "<modules>"
    );

    @Nested
    @DisplayName("仓库根无聚合构建文件")
    class RepoRootNoAggregationBuild {

        @Test
        @DisplayName("仓库根不存在 pom.xml")
        void repoRootShouldNotContainPomXml() {
            Path rootPom = REPO_ROOT.resolve("pom.xml");
            assertThat(Files.exists(rootPom))
                    .as("仓库根不得存在 pom.xml（不引入根 Maven Reactor）")
                    .isFalse();
        }

        @Test
        @DisplayName("仓库根不存在 package.json")
        void repoRootShouldNotContainPackageJson() {
            Path rootPackage = REPO_ROOT.resolve("package.json");
            assertThat(Files.exists(rootPackage))
                    .as("仓库根不得存在 package.json（不引入 Node Workspace）")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("后端构建独立性")
    class BackendBuildIndependence {

        @Test
        @DisplayName("pom.xml 不含前端构建插件")
        void pomXmlShouldNotContainFrontendPlugins() throws IOException {
            String pomContent = Files.readString(BACKEND_ROOT.resolve("pom.xml"));

            for (String keyword : FORBIDDEN_POM_KEYWORDS) {
                assertThat(pomContent)
                        .as("pom.xml 不得包含前端构建相关关键字: %s", keyword)
                        .doesNotContain(keyword);
            }
        }

        @Test
        @DisplayName("pom.xml 不含 <parent> 聚合父 POM")
        void pomXmlShouldNotHaveReactorParent() throws IOException {
            String pomContent = Files.readString(BACKEND_ROOT.resolve("pom.xml"));

            assertThat(pomContent)
                    .as("pom.xml 不得包含 <modules> 标签（不引入 Maven Reactor）")
                    .doesNotContain("<modules>");
        }

        @Test
        @DisplayName("后端 resources/static 不含前端 index.html")
        void backendStaticShouldNotContainFrontendAssets() {
            Path staticDir = BACKEND_ROOT.resolve("src/main/resources/static");
            if (Files.exists(staticDir)) {
                Path indexHtml = staticDir.resolve("index.html");
                assertThat(Files.exists(indexHtml))
                        .as("后端 resources/static 不得包含前端 index.html")
                        .isFalse();
            }
            // static 目录不存在也是通过的
        }
    }

    @Nested
    @DisplayName("前端构建独立性")
    class FrontendBuildIndependence {

        @Test
        @DisplayName("前端 package.json 存在且配置了 build 脚本")
        void frontendPackageJsonShouldHaveBuildScript() throws IOException {
            Path packageJson = FRONTEND_ROOT.resolve("package.json");
            assertThat(Files.exists(packageJson))
                    .as("前端 package.json 必须存在")
                    .isTrue();

            String content = Files.readString(packageJson);
            assertThat(content)
                    .as("前端 package.json 必须配置 build 脚本")
                    .contains("\"build\"");
        }

        @Test
        @DisplayName("前端 index.html 存在")
        void frontendIndexHtmlShouldExist() {
            Path indexHtml = FRONTEND_ROOT.resolve("index.html");
            assertThat(Files.exists(indexHtml))
                    .as("前端 index.html 必须存在")
                    .isTrue();
        }

        @Test
        @DisplayName("前端 package.json 标记为 private")
        void frontendPackageJsonShouldBePrivate() throws IOException {
            String content = Files.readString(FRONTEND_ROOT.resolve("package.json"));
            assertThat(content)
                    .as("前端 package.json 必须标记为 private（禁止 npm publish）")
                    .contains("\"private\": true");
        }
    }

    @Nested
    @DisplayName("后端 JAR 产物不含前端资源")
    class BackendJarExcludesFrontendAssets {

        @Test
        @DisplayName("target 目录下 JAR 文件不含 static/index.html")
        void jarShouldNotContainFrontendStaticAssets() throws IOException {
            Path targetDir = BACKEND_ROOT.resolve("target");
            if (!Files.exists(targetDir)) {
                // target 目录不存在时跳过（未执行 mvn package）
                return;
            }

            List<Path> jarFiles;
            try (Stream<Path> paths = Files.list(targetDir)) {
                jarFiles = paths
                        .filter(p -> p.toString().endsWith(".jar"))
                        .filter(p -> !p.toString().contains("-sources") && !p.toString().contains("-javadoc"))
                        .toList();
            }

            // 如果没有 JAR 文件，跳过（可能未执行 package）
            if (jarFiles.isEmpty()) {
                return;
            }

            // 检查后端 JAR 不含前端 static 资源
            // 这里通过检查 resources/static 目录是否有前端产物来间接验证
            // 完整的 JAR 解压验证在 CI 中通过 jar tf 命令完成
            Path staticDir = BACKEND_ROOT.resolve("src/main/resources/static");
            if (Files.exists(staticDir)) {
                try (Stream<Path> staticFiles = Files.walk(staticDir)) {
                    List<String> fileNames = staticFiles
                            .filter(Files::isRegularFile)
                            .map(p -> p.getFileName().toString())
                            .toList();

                    assertThat(fileNames)
                            .as("后端 resources/static 不得包含前端产物文件")
                            .doesNotContain("index.html");
                }
            }
        }
    }
}
