/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.wildfly.channel.app;

import static java.util.Optional.empty;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.version.Version;
import org.wildfly.channel.MavenRepository;
import org.wildfly.channel.spi.MavenVersionResolver;
import org.wildfly.channel.version.VersionComparator;

public class SimpleMavenVersionResolver implements MavenVersionResolver {

    private static String LOCAL_MAVEN_REPO = System.getProperty("user.home") + "/.m2/repository";

    @Override
    public Optional<String> resolve(String groupId, String artifactId, List<MavenRepository> mavenRepositories, boolean resolveLocalCache, VersionComparator versionComparator) {

        List<RemoteRepository> remoteRepositories = mavenRepositories.stream().map(r -> newRemoteRepository(r)).collect(Collectors.toList());

        System.out.println("remoteRepositories = " + remoteRepositories);

        System.out.println(String.format("Resolving the latest version of %s:%s in repositories: %s",
                groupId, artifactId, remoteRepositories.stream().map(r -> r.getUrl()).collect(Collectors.joining(","))));

        RepositorySystem system = newRepositorySystem();
        RepositorySystemSession session = newRepositorySystemSession(system, resolveLocalCache);

        Artifact artifact = new DefaultArtifact(groupId, artifactId, null, "[0,)");
        VersionRangeRequest versionRangeRequest = new VersionRangeRequest();
        versionRangeRequest.setArtifact(artifact);
        versionRangeRequest.setRepositories(remoteRepositories);

        try {
            VersionRangeResult versionRangeResult = system.resolveVersionRange(session, versionRangeRequest);
            List<String> versions = versionRangeResult.getVersions().stream().map(Version::toString).collect(Collectors.toList());
            System.out.println("All versions in the repositories: " + versions);

            Optional<String> found = versionComparator.matches(versions);
            System.out.println("found = " + found);
            return found;
        } catch (VersionRangeResolutionException e) {
            e.printStackTrace();
            return empty();
        }
    }

    public static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                exception.printStackTrace();
            }
        });
        return locator.getService(RepositorySystem.class);
    }

    public static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system, boolean resolveLocalCache) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        String location;
        if (resolveLocalCache) {
            location = LOCAL_MAVEN_REPO;
        } else {
           location = "target/local-repo" ;
        }
        LocalRepository localRepo = new LocalRepository(location);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        return session;
    }

    private static RemoteRepository newRemoteRepository(MavenRepository mavenRepository) {
        return new RemoteRepository.Builder(mavenRepository.getId(), "default", mavenRepository.getUrl().toExternalForm()).build();
    }
}