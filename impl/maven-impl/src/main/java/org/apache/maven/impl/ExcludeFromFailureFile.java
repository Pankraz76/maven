package org.apache.maven.impl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExcludeFromFailureFile {
    // Pattern to extract class, line number, and rule from PMD warnings
    private static final Pattern PMD_PATTERN = Pattern.compile(
            "PMD Failure: (\\S+):(\\d+) Rule:(\\S+) Priority:\\d+");

    public static void main(String[] args) {
        String pmdLogPath = "pmd.txt"; // path to PMD log file
        String propertiesPath = ".pmd/exclude.properties"; // path to exclude properties file

        try {
            // Step 1: Parse existing properties file
            Properties existingProps = new Properties();
            try (FileReader reader = new FileReader(propertiesPath)) {
                existingProps.load(reader);
            } catch (IOException e) {
                // File might not exist yet, which is okay
                System.out.println("No existing properties file found, creating new one");
            }

            // Step 2: Parse PMD log file
            Map<String, String> pmdViolations = parsePmdLog(pmdLogPath);

            // Step 3: Merge with existing properties
            Properties mergedProps = mergeProperties(existingProps, pmdViolations);

            // Step 4: Write merged properties back to file without comments/timestamps
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(propertiesPath))) {
                // Write a simple header comment if needed
                writer.write("# PMD exclusion rules");
                writer.newLine();
                writer.newLine();

                for (String key : mergedProps.stringPropertyNames()) {
                    writer.write(key + "=" + mergedProps.getProperty(key));
                    writer.newLine();
                }
            }

            System.out.println("Successfully merged PMD violations into properties file");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Map<String, String> parsePmdLog(String pmdLogPath) throws IOException {
        Map<String, String> violations = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(pmdLogPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = PMD_PATTERN.matcher(line);
                if (matcher.find()) {
                    String className = matcher.group(1);
                    String rule = matcher.group(3);

                    // Add to violations map, merging if class already exists
                    if (violations.containsKey(className)) {
                        String existingRules = violations.get(className);
                        if (!existingRules.contains(rule)) {
                            violations.put(className, existingRules + "," + rule);
                        }
                    } else {
                        violations.put(className, rule);
                    }
                }
            }
        }
        return violations;
    }

    private static Properties mergeProperties(Properties existing, Map<String, String> newViolations) {
        Properties merged = new Properties();

        // Add all existing properties first
        merged.putAll(existing);

        // Merge with new violations
        for (Map.Entry<String, String> entry : newViolations.entrySet()) {
            String className = entry.getKey();
            String newRules = entry.getValue();

            if (merged.containsKey(className)) {
                // Merge rules if class already exists
                String existingRules = merged.getProperty(className);
                for (String rule : newRules.split(",")) {
                    if (!existingRules.contains(rule)) {
                        existingRules += "," + rule;
                    }
                }
                merged.setProperty(className, existingRules);
            } else {
                // Add new entry
                merged.setProperty(className, newRules);
            }
        }

        return merged;
    }
}