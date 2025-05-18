package org.apache.maven.impl;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExcludeFromFailureFileSpotbugs {
    // Pattern to extract class and bug pattern from Spotbugs errors
    private static final Pattern SPOTBUGS_PATTERN = Pattern.compile(
            "\\[ERROR\\] \\w+: (\\S+) (?:\\[\\S+\\] )?\\[\\S+\\] At \\S+\\.java:\\S+\\] (\\S+)");

    public static void main(String[] args) {
        String spotbugsLogPath = "spotbugs.txt"; // path to Spotbugs log file
        String xmlPath = ".spotbugs/spotbugs-exclude.xml"; // path to exclude XML file

        try {
            // Step 1: Parse existing XML file
            Set<String> existingExclusions = loadExistingExclusions(xmlPath);

            // Step 2: Parse Spotbugs log file
            Map<String, String> newViolations = parseSpotbugsLog(spotbugsLogPath);

            // Step 3: Generate XML content
            String xmlContent = generateXmlContent(existingExclusions, newViolations);

            // Step 4: Write XML file
            writeXmlFile(xmlContent, xmlPath);

            System.out.println("Successfully updated Spotbugs exclusion file");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Set<String> loadExistingExclusions(String xmlPath) throws IOException {
        Set<String> exclusions = new TreeSet<>();
        File file = new File(xmlPath);
        if (!file.exists()) {
            return exclusions;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(xmlPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("<Class name=") && line.contains("<Bug pattern=")) {
                    exclusions.add(line.trim());
                }
            }
        }
        return exclusions;
    }

    private static Map<String, String> parseSpotbugsLog(String logPath) throws IOException {
        Map<String, String> violations = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(logPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = SPOTBUGS_PATTERN.matcher(line);
                if (matcher.find()) {
                    String className = matcher.group(1);
                    String bugPattern = matcher.group(2);

                    // Add to violations map, merging if class already exists
                    if (violations.containsKey(className)) {
                        String existingPatterns = violations.get(className);
                        if (!existingPatterns.contains(bugPattern)) {
                            violations.put(className, existingPatterns + "," + bugPattern);
                        }
                    } else {
                        violations.put(className, bugPattern);
                    }
                }
            }
        }
        return violations;
    }

    private static String generateXmlContent(Set<String> existingExclusions, Map<String, String> newViolations) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<FindBugsFilter>\n");

        // Add existing exclusions
        for (String exclusion : existingExclusions) {
            xml.append("  ").append(exclusion).append("\n");
        }

        // Add new violations
        for (Map.Entry<String, String> entry : newViolations.entrySet()) {
            String className = entry.getKey();
            String[] bugPatterns = entry.getValue().split(",");

            for (String pattern : bugPatterns) {
                xml.append("  <Match>\n");
                xml.append("    <Class name=\"").append(className).append("\"/>\n");
                xml.append("    <Bug pattern=\"").append(pattern).append("\"/>\n");
                xml.append("  </Match>\n");
            }
        }

        xml.append("</FindBugsFilter>");
        return xml.toString();
    }

    private static void writeXmlFile(String content, String path) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            writer.write(content);
        }
    }
}