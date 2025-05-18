package org.apache.maven.impl;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpotbugsExclusionGenerator {
    private static final Pattern SPOTBUGS_PATTERN = Pattern.compile(
            "\\[ERROR\\] (?:Medium|High|Low): ([\\w.$]+)(?:#([\\w]+)\\(([^)]*)\\))?\\s*(?:\\[([A-Z_]+)\\]|may expose internal representation)"
    );

    private static final Pattern EXISTING_EXCLUSION_PATTERN = Pattern.compile(
            "<Match>\\s*<Class name=\"([^\"]+)\"\\s*/>(?:\\s*<Method name=\"([^\"]+)\"\\s*/>)?\\s*<Bug pattern=\"([^\"]+)\"\\s*/>");

    public static void main(String[] args) {
        String spotbugsLogPath = "spotbugs.txt";
        String xmlOutputPath = ".spotbugs/spotbugs-exclude.xml";

        try {
            Set<BugInstance> newBugs = parseSpotbugsLog(spotbugsLogPath);
            Set<BugInstance> existingBugs = readExistingExclusions(xmlOutputPath);
            existingBugs.addAll(newBugs);
            String xmlContent = generateXmlExclusions(existingBugs);
            writeXmlFile(xmlContent, xmlOutputPath);

            System.out.println("Successfully updated Spotbugs exclusion file: " + xmlOutputPath);
            System.out.println("Total exclusions: " + existingBugs.size());
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
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
                    String methodName = matcher.group(2);
                    String methodParams = matcher.group(3);
                    String bugPattern = matcher.group(4) != null ? matcher.group(4) :
                            (line.contains("may expose internal representation") ?
                                    (line.contains(" storing ") ? "EI_EXPOSE_REP2" : "EI_EXPOSE_REP") : null);

                    if (bugPattern != null) {
                        String fullMethod = methodName != null ?
                                methodName + "(" + (methodParams != null ? methodParams : "") + ")" : null;
                        bugs.add(new BugInstance(className, fullMethod, bugPattern));
                        System.out.println("New bug found: " + className +
                                (fullMethod != null ? "#" + fullMethod : "") +
                                " - " + bugPattern);
                    }
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

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }

        Matcher matcher = EXISTING_EXCLUSION_PATTERN.matcher(content);
        while (matcher.find()) {
            String className = matcher.group(1);
            String method = matcher.group(2);
            String bugPattern = matcher.group(3);
            existingBugs.add(new BugInstance(className, method, bugPattern));
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
            if (bug.getMethod() != null) {
                xml.append("    <Method name=\"").append(bug.getMethod()).append("\"/>\n");
            }
            xml.append("    <Bug pattern=\"").append(bug.getBugPattern()).append("\"/>\n");
            xml.append("  </Match>\n");
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
        private final String method;
        private final String bugPattern;

        public BugInstance(String className, String method, String bugPattern) {
            this.className = className;
            this.method = method;
            this.bugPattern = bugPattern;
        }

        public String getClassName() {
            return className;
        }

        public String getMethod() {
            return method;
        }

        public String getBugPattern() {
            return bugPattern;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BugInstance that = (BugInstance) o;
            return className.equals(that.className) &&
                    (method != null ? method.equals(that.method) : that.method == null) &&
                    bugPattern.equals(that.bugPattern);
        }

        @Override
        public int hashCode() {
            int result = className.hashCode();
            result = 31 * result + (method != null ? method.hashCode() : 0);
            result = 31 * result + bugPattern.hashCode();
            return result;
        }
    }
}