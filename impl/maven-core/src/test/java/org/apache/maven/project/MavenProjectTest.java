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
package org.apache.maven.project;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.model.Build;
import org.apache.maven.model.CiManagement;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Developer;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Prerequisites;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenProjectTest extends AbstractMavenProjectTestCase {

    @Test
    void testShouldInterpretChildPathAdjustmentBasedOnModulePaths() throws IOException {
        Model parentModel = new Model();
        parentModel.addModule("../child");

        MavenProject parentProject = new MavenProject(parentModel);

        Model childModel = new Model();
        childModel.setArtifactId("artifact");

        MavenProject childProject = new MavenProject(childModel);

        File childFile = new File(
                System.getProperty("java.io.tmpdir"),
                "maven-project-tests" + System.currentTimeMillis() + "/child/pom.xml");

        childProject.setFile(childFile);

        String adjustment = parentProject.getModulePathAdjustment(childProject);

        assertNotNull(adjustment);

        assertEquals("..", adjustment);
    }

    @Test
    void testIdentityProtoInheritance() {
        Parent parent = new Parent();

        parent.setGroupId("test-group");
        parent.setVersion("1000");
        parent.setArtifactId("test-artifact");

        Model model = new Model();

        model.setParent(parent);
        model.setArtifactId("real-artifact");

        MavenProject project = new MavenProject(model);

        assertEquals("test-group", project.getGroupId(), "groupId proto-inheritance failed.");
        assertEquals("real-artifact", project.getArtifactId(), "artifactId is masked.");
        assertEquals("1000", project.getVersion(), "version proto-inheritance failed.");

        // draw the NPE.
        project.getId();
    }

    @Test
    void testEmptyConstructor() {
        MavenProject project = new MavenProject();

        assertEquals(
                MavenProject.EMPTY_PROJECT_GROUP_ID + ":" + MavenProject.EMPTY_PROJECT_ARTIFACT_ID + ":jar:"
                        + MavenProject.EMPTY_PROJECT_VERSION,
                project.getId());
    }

    @Test
    void testClone() throws Exception {
        File f = getFileForClasspathResource("canonical-pom.xml");
        MavenProject projectToClone = getProject(f);

        MavenProject clonedProject = projectToClone.clone();
        assertEquals("maven-core", clonedProject.getArtifactId());
        Map<?, ?> clonedMap = clonedProject.getManagedVersionMap();
        assertNotNull(clonedMap, "ManagedVersionMap not copied");
        assertTrue(clonedMap.isEmpty(), "ManagedVersionMap is not empty");
    }

    @Test
    void testCloneWithDependencyManagement() throws Exception {
        File f = getFileForClasspathResource("dependencyManagement-pom.xml");
        MavenProject projectToClone = getProjectWithDependencies(f);
        DependencyManagement dep = projectToClone.getDependencyManagement();
        assertNotNull(dep, "No dependencyManagement");
        List<?> list = dep.getDependencies();
        assertNotNull(list, "No dependencies");
        assertTrue(!list.isEmpty(), "Empty dependency list");

        Map<?, ?> map = projectToClone.getManagedVersionMap();
        assertNotNull(map, "No ManagedVersionMap");
        assertTrue(!map.isEmpty(), "ManagedVersionMap is empty");

        MavenProject clonedProject = projectToClone.clone();
        assertEquals("maven-core", clonedProject.getArtifactId());
        Map<?, ?> clonedMap = clonedProject.getManagedVersionMap();
        assertNotNull(clonedMap, "ManagedVersionMap not copied");
        assertTrue(!clonedMap.isEmpty(), "ManagedVersionMap is empty");
        assertTrue(clonedMap.containsKey("maven-test:maven-test-b:jar"), "ManagedVersionMap does not contain test key");
    }

    @Test
    void testGetModulePathAdjustment() throws IOException {
        Model moduleModel = new Model();

        MavenProject module = new MavenProject(moduleModel);
        module.setFile(new File("module-dir/pom.xml"));

        Model parentModel = new Model();
        parentModel.addModule("../module-dir");

        MavenProject parent = new MavenProject(parentModel);
        parent.setFile(new File("parent-dir/pom.xml"));

        String pathAdjustment = parent.getModulePathAdjustment(module);

        assertEquals("..", pathAdjustment);
    }

    @Test
    void testCloneWithDistributionManagement() throws Exception {

        File f = getFileForClasspathResource("distributionManagement-pom.xml");
        MavenProject projectToClone = getProject(f);

        MavenProject clonedProject = projectToClone.clone();
        assertNotNull(
                clonedProject.getDistributionManagementArtifactRepository(), "clonedProject - distributionManagement");
    }

    @Test
    void testCloneWithActiveProfile() throws Exception {

        File f = getFileForClasspathResource("withActiveByDefaultProfile-pom.xml");
        MavenProject projectToClone = getProject(f);
        List<Profile> activeProfilesOrig = projectToClone.getActiveProfiles();

        assertEquals(1, activeProfilesOrig.size(), "Expecting 1 active profile");

        MavenProject clonedProject = projectToClone.clone();

        List<Profile> activeProfilesClone = clonedProject.getActiveProfiles();

        assertEquals(1, activeProfilesClone.size(), "Expecting 1 active profile");

        assertNotSame(
                activeProfilesOrig,
                activeProfilesClone,
                "The list of active profiles should have been cloned too but is same");
    }

    @Test
    void testCloneWithBaseDir() throws Exception {
        File f = getFileForClasspathResource("canonical-pom.xml");
        MavenProject projectToClone = getProject(f);
        projectToClone.setPomFile(new File(new File(f.getParentFile(), "target"), "flattened.xml"));
        MavenProject clonedProject = projectToClone.clone();
        assertEquals(projectToClone.getFile(), clonedProject.getFile(), "POM file is preserved across clone");
        assertEquals(
                projectToClone.getBasedir(), clonedProject.getBasedir(), "Base directory is preserved across clone");
    }

    @Test
    void testUndefinedOutputDirectory() throws Exception {
        MavenProject p = new MavenProject();
        assertNoNulls(p.getCompileClasspathElements());
        assertNoNulls(p.getSystemClasspathElements());
        assertNoNulls(p.getRuntimeClasspathElements());
        assertNoNulls(p.getTestClasspathElements());
    }

    @Test
    void testAddDotFile() {
        MavenProject project = new MavenProject();

        File basedir = new File(System.getProperty("java.io.tmpdir"));
        project.setFile(new File(basedir, "file"));

        project.addCompileSourceRoot(basedir.getAbsolutePath());
        project.addCompileSourceRoot(".");

        assertEquals(1, project.getCompileSourceRoots().size());
    }

    private void assertNoNulls(List<String> elements) {
        assertFalse(elements.contains(null));
    }

    @Test
    void testGetArtifacts() {
        // Setup artifact handler
        ArtifactHandler handler = new DefaultArtifactHandler("jar");

        // Test case 1: artifacts is not null (should return cached artifacts)
        MavenProject project1 = new MavenProject();
        Set<Artifact> existingArtifacts = new LinkedHashSet<>();
        existingArtifacts.add(new DefaultArtifact("group", "artifact", "1.0", "compile", "jar", "classifier", handler));
        project1.setArtifacts(existingArtifacts);
        assertEquals(existingArtifacts, project1.getArtifacts());

        // Test case 2: artifacts is null and either filter or resolved artifacts is null
        MavenProject project2 = new MavenProject();
        assertNotNull(project2.getArtifacts());
        assertTrue(project2.getArtifacts().isEmpty());

        // Test case 3: artifacts is null but filter and resolved artifacts exist
        MavenProject project3 = new MavenProject();
        Set<Artifact> resolvedArtifacts = new LinkedHashSet<>();
        Artifact artifact1 = new DefaultArtifact("group1", "artifact1", "1.0", "compile", "jar", null, handler);
        Artifact artifact2 = new DefaultArtifact("group2", "artifact2", "1.0", "test", "jar", null, handler);
        resolvedArtifacts.add(artifact1);
        resolvedArtifacts.add(artifact2);

        project3.setResolvedArtifacts(resolvedArtifacts);

        // Create a filter that includes only compile scope artifacts
        project3.setArtifactFilter(artifact -> "compile".equals(artifact.getScope()));

        Set<Artifact> filteredArtifacts = project3.getArtifacts();
        assertEquals(1, filteredArtifacts.size(), "Should filter artifacts based on include filter");
        assertTrue(filteredArtifacts.contains(artifact1), "Should include artifact with compile scope");
        assertFalse(filteredArtifacts.contains(artifact2), "Should exclude artifact with test scope");

        // Verify the result is cached
        assertSame(filteredArtifacts, project3.getArtifacts(), "Should cache the filtered artifacts");
    }

    @Test
    void testSetAndGetAttachedArtifacts() {
        MavenProject project = new MavenProject();
        List<Artifact> attachedArtifacts = new java.util.ArrayList<>();
        ArtifactHandler handler = new DefaultArtifactHandler("jar");
        attachedArtifacts.add(new DefaultArtifact("group", "attached-artifact", "1.0", null, "jar", "test", handler));
        project.setAttachedArtifacts(attachedArtifacts);

        assertNotNull(project.getAttachedArtifacts());
        assertEquals(1, project.getAttachedArtifacts().size());
        assertEquals("attached-artifact", project.getAttachedArtifacts().get(0).getArtifactId());
        // Change assertSame to assertEquals to compare content, not reference
        assertEquals(
                attachedArtifacts, project.getAttachedArtifacts(), "Should return an equal list of attached artifacts");
    }

    @Test
    void testGetBuildPlugins() {
        MavenProject project = new MavenProject();

        // Test with no build plugins
        assertNotNull(project.getBuildPlugins());
        assertTrue(project.getBuildPlugins().isEmpty());

        // Test with build plugins defined
        Model model = project.getModel();
        Build build = new Build();
        Plugin plugin1 = new Plugin();
        plugin1.setGroupId("org.apache.maven.plugins");
        plugin1.setArtifactId("maven-clean-plugin");
        Plugin plugin2 = new Plugin();
        plugin2.setGroupId("org.apache.maven.plugins");
        plugin2.setArtifactId("maven-install-plugin");
        build.addPlugin(plugin1);
        build.addPlugin(plugin2);
        model.setBuild(build);
        project.setModel(model);

        List<Plugin> buildPlugins = project.getBuildPlugins();
        assertNotNull(buildPlugins);
        assertEquals(2, buildPlugins.size());
        assertEquals("maven-clean-plugin", buildPlugins.get(0).getArtifactId());
        assertEquals("maven-install-plugin", buildPlugins.get(1).getArtifactId());
    }

    @Test
    void testGetDistributionManagement() {
        MavenProject project = new MavenProject();

        // Test with no distribution management
        assertNull(project.getDistributionManagement());

        // Test with distribution management defined
        Model model = project.getModel();
        DistributionManagement dm = new DistributionManagement();
        dm.setDownloadUrl("http://example.com/downloads");
        model.setDistributionManagement(dm);
        project.setModel(model);

        assertNotNull(project.getDistributionManagement());
        assertEquals(
                "http://example.com/downloads",
                project.getDistributionManagement().getDownloadUrl());
    }

    @Test
    void testGetCiManagement() {
        MavenProject project = new MavenProject();
        assertNull(project.getCiManagement());

        Model model = project.getModel();
        CiManagement ci = new CiManagement();
        ci.setSystem("Jenkins");
        model.setCiManagement(ci);
        project.setModel(model);

        assertNotNull(project.getCiManagement());
        assertEquals("Jenkins", project.getCiManagement().getSystem());
    }

    @Test
    void testGetIssueManagement() {
        MavenProject project = new MavenProject();
        assertNull(project.getIssueManagement());

        Model model = project.getModel();
        IssueManagement issue = new IssueManagement();
        issue.setSystem("JIRA");
        model.setIssueManagement(issue);
        project.setModel(model);

        assertNotNull(project.getIssueManagement());
        assertEquals("JIRA", project.getIssueManagement().getSystem());
    }

    @Test
    void testGetScm() {
        MavenProject project = new MavenProject();
        assertNull(project.getScm());

        Model model = project.getModel();
        org.apache.maven.model.Scm scm = new org.apache.maven.model.Scm();
        scm.setConnection("scm:git:http://example.com/repo.git");
        model.setScm(scm);
        project.setModel(model);

        assertNotNull(project.getScm());
        assertEquals("scm:git:http://example.com/repo.git", project.getScm().getConnection());
    }

    @Test
    void testGetDevelopers() {
        MavenProject project = new MavenProject();
        assertNotNull(project.getDevelopers());
        assertTrue(project.getDevelopers().isEmpty());

        Model model = project.getModel();
        Developer dev = new Developer();
        dev.setId("johndoe");
        model.addDeveloper(dev);
        project.setModel(model);

        assertEquals(1, project.getDevelopers().size());
        assertEquals("johndoe", project.getDevelopers().get(0).getId());
    }

    @Test
    void testGetContributors() {
        MavenProject project = new MavenProject();
        assertNotNull(project.getContributors());
        assertTrue(project.getContributors().isEmpty());

        Model model = project.getModel();
        Contributor contrib = new Contributor();
        contrib.setName("Jane Doe");
        model.addContributor(contrib);
        project.setModel(model);

        assertEquals(1, project.getContributors().size());
        assertEquals("Jane Doe", project.getContributors().get(0).getName());
    }

    @Test
    void testGetLicenses() {
        MavenProject project = new MavenProject();
        assertNotNull(project.getLicenses());
        assertTrue(project.getLicenses().isEmpty());

        Model model = project.getModel();
        License license = new License();
        license.setName("Apache License 2.0");
        model.addLicense(license);
        project.setModel(model);

        assertEquals(1, project.getLicenses().size());
        assertEquals("Apache License 2.0", project.getLicenses().get(0).getName());
    }

    @Test
    void testGetMailingLists() {
        MavenProject project = new MavenProject();
        assertNotNull(project.getMailingLists());
        assertTrue(project.getMailingLists().isEmpty());

        Model model = project.getModel();
        MailingList ml = new MailingList();
        ml.setName("dev");
        model.addMailingList(ml);
        project.setModel(model);

        assertEquals(1, project.getMailingLists().size());
        assertEquals("dev", project.getMailingLists().get(0).getName());
    }

    @Test
    void testGetOrganization() {
        MavenProject project = new MavenProject();
        assertNull(project.getOrganization());

        Model model = project.getModel();
        Organization org = new Organization();
        org.setName("Apache Software Foundation");
        model.setOrganization(org);
        project.setModel(model);

        assertNotNull(project.getOrganization());
        assertEquals("Apache Software Foundation", project.getOrganization().getName());
    }

    @Test
    void testGetPrerequisites() {
        MavenProject project = new MavenProject();
        assertNull(project.getPrerequisites());

        Model model = project.getModel();
        Prerequisites pre = new Prerequisites();
        pre.setMaven("3.6");
        model.setPrerequisites(pre);
        project.setModel(model);

        assertNotNull(project.getPrerequisites());
        assertEquals("3.6", project.getPrerequisites().getMaven());
    }

    @Test
    void testGetRepositories() {
        MavenProject project = new MavenProject();
        assertNotNull(project.getRepositories());
        assertTrue(project.getRepositories().isEmpty());

        Model model = project.getModel();
        Repository repo = new Repository();
        repo.setId("my-repo");
        model.addRepository(repo);
        project.setModel(model);

        assertEquals(1, project.getRepositories().size());
        assertEquals("my-repo", project.getRepositories().get(0).getId());
    }

    @Test
    void testGetPluginRepositories() {
        MavenProject project = new MavenProject();
        assertNotNull(project.getPluginRepositories());
        assertTrue(project.getPluginRepositories().isEmpty());

        Model model = project.getModel();
        Repository pluginRepo = new Repository();
        pluginRepo.setId("my-plugin-repo");
        model.addPluginRepository(pluginRepo);
        project.setModel(model);

        assertEquals(1, project.getPluginRepositories().size());
        assertEquals("my-plugin-repo", project.getPluginRepositories().get(0).getId());
    }

    @Test
    void testGetProperties() {
        MavenProject project = new MavenProject();
        assertNotNull(project.getProperties());
        assertTrue(project.getProperties().isEmpty());

        Model model = project.getModel();
        Properties props = new Properties();
        props.setProperty("my.property", "value");
        model.setProperties(props);
        project.setModel(model);

        assertNotNull(project.getProperties());
        assertEquals("value", project.getProperties().getProperty("my.property"));
    }

    @Test
    void testSetModel() {
        MavenProject project = new MavenProject();
        assertNotNull(project.getModel());
        assertEquals(MavenProject.EMPTY_PROJECT_ARTIFACT_ID, project.getArtifactId());

        Model newModel = new Model();
        newModel.setArtifactId("new-artifact");
        project.setModel(newModel);

        assertNotNull(project.getModel());
        assertEquals("new-artifact", project.getArtifactId());
        assertSame(newModel, project.getModel()); // Verify that the model object itself is set
    }
}
