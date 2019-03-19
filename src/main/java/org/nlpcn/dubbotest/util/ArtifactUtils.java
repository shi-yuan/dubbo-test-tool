package org.nlpcn.dubbotest.util;

import com.alibaba.dubbo.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileSystemUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public class ArtifactUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ArtifactUtils.class);

    public static Path download(String localRepository, String groupId, String artifactId, String version) throws IOException {
        Path path = Paths.get(localRepository, StringUtils.split(groupId, '.')).resolve(artifactId).resolve(version);
        if (!Files.exists(path)) {
            Path dir = Files.createTempDirectory(null);
            LOG.info("prepare to download artifact[{}:{}:{}] after creating temp directory[{}]", groupId, artifactId, version, dir);
            try {
                Files.write(dir.resolve("pom.xml"), Arrays.asList(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">",
                        "<modelVersion>4.0.0</modelVersion><groupId>org.nlpcn</groupId><artifactId>dubbotest</artifactId><version>0.0.1-SNAPSHOT</version><dependencies>",
                        String.format("<dependency><groupId>%s</groupId><artifactId>%s</artifactId><version>%s</version></dependency>",
                                groupId, artifactId, version),
                        "</dependencies></project>"), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                try {
                    Files.createDirectories(path);
                    if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                        execute(dir.toFile(), "cmd", "/c", "mvn", "-f", "pom.xml", "dependency:copy-dependencies", "-DoutputDirectory=" + path.toFile().getAbsolutePath());
                    } else {
                        execute(dir.toFile(), "mvn", "-f", "pom.xml", "dependency:copy-dependencies", "-DoutputDirectory=" + path.toFile().getAbsolutePath());
                    }
                } catch (Exception ex) {
                    FileSystemUtils.deleteRecursively(path);
                    throw new IllegalStateException(ex);
                }
            } finally {
                boolean ret = FileSystemUtils.deleteRecursively(dir);
                LOG.info("download[{}:{}:{}] end, delete temp directory[{}]: {}", groupId, artifactId, version, dir, ret);
            }
        } else {
            LOG.info("path already exists: {}", path);
        }

        return path;
    }

    private static String execute(File baseDir, String... args) throws IOException, InterruptedException {
        LOG.info("exec: {}", Arrays.toString(args));

        StringBuilder sb = new StringBuilder();

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(baseDir);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        try {
            LOG.info("Process started !");

            String line;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                while ((line = in.readLine()) != null) {
                    sb.append(line).append("\n");
                    LOG.info(line);
                }
            }

            proc.waitFor();

            LOG.info("Process ended !");
        } finally {
            proc.destroy();
        }

        return sb.toString();
    }

}
