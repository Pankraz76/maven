package org.apache.maven.impl;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class ExcludeFromFailureFile {

    private static final Pattern PMD_PATTERN = Pattern.compile(
            "PMD Failure: ([\\w\\.]+):(\\d+) Rule:([\\w]+) Priority:\\d+");

    public static void main(String[] args) throws IOException {
        Path logPath = Paths.get("pmd.txt"); // path to pmd.log
        Path propsPath = Paths.get(".pmd/exclude.properties"); // path to exclude.properties

        // Load existing properties
        Properties excludeProps = new Properties();
        if (Files.exists(propsPath)) {
            try (InputStream in = Files.newInputStream(propsPath)) {
                excludeProps.load(in);
            }
        }

        // Parse PMD log and collect new rules
        Map<String, Set<String>> newEntries = new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(logPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = PMD_PATTERN.matcher(line);
                if (matcher.find()) {
                    String className = matcher.group(1);
                    String rule = matcher.group(3);

                    newEntries.computeIfAbsent(className, k -> new HashSet<>()).add(rule);
                }
            }
        }

        // Merge with existing properties
        for (Map.Entry<String, Set<String>> entry : newEntries.entrySet()) {
            String key = entry.getKey();
            Set<String> newRules = entry.getValue();

            String existing = excludeProps.getProperty(key);
            Set<String> merged = new TreeSet<>(newRules); // TreeSet to sort and deduplicate

            if (existing != null && !existing.isEmpty()) {
                merged.addAll(Arrays.asList(existing.split(",")));
            }

            excludeProps.setProperty(key, String.join(",", merged));
        }

        // Ensure output directory exists
        Files.createDirectories(propsPath.getParent());

        // Write back merged properties
        try (OutputStream out = Files.newOutputStream(propsPath)) {
            excludeProps.store(out, "Merged PMD exclusions");
        }

        System.out.println("Exclude file updated successfully.");
    }
}
