package com.zhicore.common.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDateTimeUsageGuardTest {

    @Test
    void productionCodeShouldNotUseLocalDateTime() throws IOException {
        Path projectRoot = resolveProjectRoot();

        List<String> violations;
        try (Stream<Path> files = Files.walk(projectRoot)) {
            violations = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("/.worktrees/"))
                    .filter(this::containsLocalDateTime)
                    .map(projectRoot::relativize)
                    .map(Path::toString)
                    .sorted()
                    .collect(Collectors.toList());
        }

        assertTrue(violations.isEmpty(), () -> "生产代码仍存在 LocalDateTime:\n" + String.join("\n", violations));
    }

    private Path resolveProjectRoot() {
        String multiModuleDir = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleDir != null && !multiModuleDir.isBlank()) {
            return Path.of(multiModuleDir).toAbsolutePath().normalize();
        }
        return Path.of("").toAbsolutePath().normalize().getParent();
    }

    private boolean containsLocalDateTime(Path file) {
        try {
            return Files.readString(file).contains("LocalDateTime");
        } catch (IOException e) {
            throw new IllegalStateException("读取文件失败: " + file, e);
        }
    }
}
