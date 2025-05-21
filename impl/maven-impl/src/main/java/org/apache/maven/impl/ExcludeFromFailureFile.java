/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExcludeFromFailureFile {

    public static void main(String[] args) throws IOException {
        // Process both formats
        Path logPath = Paths.get("pmd.txt");
        Path propsPath = Paths.get(".pmd/exclude.properties");

        // First process path-style format
        PathStyleProcessor.process(logPath, propsPath);

        // Then process dot-style format
        DotStyleProcessor.process(logPath, propsPath);

        System.out.println("Exclude file updated successfully.");
    }

    public static class PathStyleProcessor {
        private static final Pattern PMD_PATTERN =
                Pattern.compile("PMD Failure: ([\\w/]+)\\.java:(\\d+) Rule:(\\w+) Priority:\\d+ .*");

        public static void process(Path logPath, Path propsPath) throws IOException {
            Properties excludeProps = loadProperties(propsPath);
            Map<String, Set<String>> newEntries = new HashMap<>();

            try (BufferedReader reader = Files.newBufferedReader(logPath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = PMD_PATTERN.matcher(line);
                    if (matcher.find()) {
                        String className = matcher.group(1).replace('/', '.');
                        String rule = matcher.group(3);
                        newEntries
                                .computeIfAbsent(className, k -> new HashSet<>())
                                .add(rule);
                    }
                }
            }

            mergeAndSave(excludeProps, newEntries, propsPath);
        }
    }

    public static class DotStyleProcessor {
        private static final Pattern PMD_PATTERN =
                Pattern.compile("PMD Failure: ([\\w.]+):(\\d+) Rule:(\\w+) Priority:\\d+ .*");

        public static void process(Path logPath, Path propsPath) throws IOException {
            Properties excludeProps = loadProperties(propsPath);
            Map<String, Set<String>> newEntries = new HashMap<>();

            try (BufferedReader reader = Files.newBufferedReader(logPath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = PMD_PATTERN.matcher(line);
                    if (matcher.find()) {
                        String className = matcher.group(1);
                        String rule = matcher.group(3);
                        newEntries
                                .computeIfAbsent(className, k -> new HashSet<>())
                                .add(rule);
                    }
                }
            }

            mergeAndSave(excludeProps, newEntries, propsPath);
        }
    }

    private static Properties loadProperties(Path propsPath) throws IOException {
        Properties props = new Properties();
        if (Files.exists(propsPath)) {
            try (InputStream in = Files.newInputStream(propsPath)) {
                props.load(in);
            }
        }
        return props;
    }

    private static void mergeAndSave(Properties excludeProps, Map<String, Set<String>> newEntries, Path propsPath)
            throws IOException {
        for (Map.Entry<String, Set<String>> entry : newEntries.entrySet()) {
            String key = entry.getKey();
            Set<String> newRules = entry.getValue();
            String existing = excludeProps.getProperty(key);
            Set<String> merged = new TreeSet<>(newRules);

            if (existing != null && !existing.isEmpty()) {
                merged.addAll(Arrays.asList(existing.split(",")));
            }

            excludeProps.setProperty(key, String.join(",", merged));
        }

        Files.createDirectories(propsPath.getParent());
        try (OutputStream out = Files.newOutputStream(propsPath)) {
            excludeProps.store(out, "Merged PMD exclusions");
        }
    }
}
