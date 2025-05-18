package org.apache.maven.impl;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpotbugsExclusionGenerator {
    // Pattern to extract class and bug pattern from Spotbugs errors
    private static final Pattern SPOTBUGS_PATTERN = Pattern.compile(
            "\\[ERROR\\] \\w+: (\\S+) (?:\\[\\S+\\] )?\\[\\S+\\] At \\S+\\.java:\\S+\\] (\\S+)");

    public static void main(String[] args) {
        String spotbugsLogPath = "spotbugs.log"; // path to Spotbugs log file
        String xmlOutputPath = ".spotbugs/spotbugs-exclude.xml"; // path to output XML file

        try {
            // Step 1: Parse the Spotbugs log file
            Set<BugInstance> bugInstances = parseSpotbugsLog(spotbugsLogPath);

            // Step 2: Generate XML content
            String xmlContent = generateXmlExclusions(bugInstances);

            // Step 3: Write to XML file
            writeXmlFile(xmlContent, xmlOutputPath);

            System.out.println("Successfully generated Spotbugs exclusion file: " + xmlOutputPath);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Set<BugInstance> parseSpotbugsLog(String logPath) throws IOException {
        Set<BugInstance> bugInstances = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(logPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = SPOTBUGS_PATTERN.matcher(line);
                if (matcher.find()) {
                    String className = matcher.group(1);
                    String bugPattern = matcher.group(2);
                    bugInstances.add(new BugInstance(className, bugPattern));
                }
            }
        }
        return bugInstances;
    }

    private static String generateXmlExclusions(Set<BugInstance> bugInstances) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<FindBugsFilter>\n");

        // Add header comment
        xml.append("  <!-- Generated Spotbugs exclusions -->\n");
        xml.append("  <!-- This file was automatically generated from Spotbugs output -->\n\n");

        // Add each bug instance as a Match entry
        for (BugInstance bug : bugInstances) {
            xml.append("  <Match>\n");
            xml.append("    <Class name=\"").append(bug.getClassName()).append("\"/>\n");
            xml.append("    <Bug pattern=\"").append(bug.getBugPattern()).append("\"/>\n");
            xml.append("  </Match>\n\n");
        }

        xml.append("</FindBugsFilter>");
        return xml.toString();
    }

    private static void writeXmlFile(String content, String path) throws IOException {
        // Create parent directories if they don't exist
        File file = new File(path);
        file.getParentFile().mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(content);
        }
    }

    private static class BugInstance {
        private final String className;
        private final String bugPattern;

        public BugInstance(String className, String bugPattern) {
            this.className = className;
            this.bugPattern = bugPattern;
        }

        public String getClassName() {
            return className;
        }

        public String getBugPattern() {
            return bugPattern;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BugInstance that = (BugInstance) o;
            return className.equals(that.className) && bugPattern.equals(that.bugPattern);
        }

        @Override
        public int hashCode() {
            return 31 * className.hashCode() + bugPattern.hashCode();
        }
    }
}