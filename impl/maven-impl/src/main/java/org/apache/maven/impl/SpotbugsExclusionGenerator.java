package org.apache.maven.impl;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpotbugsExclusionGenerator {
    private static final Pattern SPOTBUGS_PATTERN = Pattern.compile(
            "\\[ERROR\\] \\w+: .*?\\[(.*?)\\] At .*?:\\[line \\d+\\] (\\S+)");
    private static final Pattern EXISTING_EXCLUSION_PATTERN = Pattern.compile(
            "<Class name=\"([^\"]+)\"\\s*/>\\s*<Bug pattern=\"([^\"]+)\"\\s*/>");

    public static void main(String[] args) {
        String spotbugsLogPath = "spotbugs.txt";
        String xmlOutputPath = ".spotbugs/spotbugs-exclude.xml";

        try {
            // Step 1: Parse SpotBugs log
            Set<BugInstance> newBugs = parseSpotbugsLog(spotbugsLogPath);

            // Step 2: Read existing XML exclusions (if present)
            Set<BugInstance> existingBugs = readExistingExclusions(xmlOutputPath);

            // Step 3: Merge new and existing bugs
            existingBugs.addAll(newBugs); // merge

            // Step 4: Generate and write merged XML
            String xmlContent = generateXmlExclusions(existingBugs);
            writeXmlFile(xmlContent, xmlOutputPath);

            System.out.println("Successfully updated Spotbugs exclusion file: " + xmlOutputPath);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Set<BugInstance> parseSpotbugsLog(String logPath) throws IOException {
        Set<BugInstance> bugs = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(logPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = SPOTBUGS_PATTERN.matcher(line);
                if (matcher.find()) {
                    String className = matcher.group(1);
                    String bugPattern = matcher.group(2);
                    bugs.add(new BugInstance(className, bugPattern));
                    System.out.println("New bug found: " + className + " - " + bugPattern);
                }
            }
        }
        return bugs;
    }

    private static Set<BugInstance> readExistingExclusions(String path) throws IOException {
        Set<BugInstance> existingBugs = new HashSet<>();
        File file = new File(path);
        if (!file.exists()) {
            return existingBugs;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder xml = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                xml.append(line).append("\n");
            }

            Matcher matcher = EXISTING_EXCLUSION_PATTERN.matcher(xml.toString());
            while (matcher.find()) {
                String className = matcher.group(1);
                String bugPattern = matcher.group(2);
                existingBugs.add(new BugInstance(className, bugPattern));
                System.out.println("Existing exclusion loaded: " + className + " - " + bugPattern);
            }
        }

        return existingBugs;
    }

    private static String generateXmlExclusions(Set<BugInstance> bugInstances) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<FindBugsFilter>\n");

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
