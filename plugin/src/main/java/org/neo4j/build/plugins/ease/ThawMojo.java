/**
 * Copyright (c) 2012-2012 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.build.plugins.ease;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.artifact.filter.StrictPatternExcludesArtifactFilter;
import org.apache.maven.shared.artifact.filter.StrictPatternIncludesArtifactFilter;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilderException;
import org.apache.maven.shared.dependency.tree.traversal.CollectingDependencyNodeVisitor;
import org.codehaus.plexus.util.FileUtils;

/**
 * Goal which prepares artifacts for install or deploy.
 * 
 * @goal thaw
 * @phase verify
 * @threadSafe true
 */
public class ThawMojo extends AbstractMojo
{
    /**
     * Patterns for artifacts to include. The pattern format is:
     * [groupId]:[artifactId]:[type]:[version]
     * 
     * @parameter
     */
    protected List<String> includes;

    /**
     * Patterns for artifacts to exclude. The pattern format is:
     * [groupId]:[artifactId]:[type]:[version]
     * 
     * @parameter
     */
    protected List<String> excludes;

    /**
     * If we should exclude transitive dependencies
     * 
     * @parameter expression="${excludeTransitive}" default-value="false"
     */
    protected boolean excludeTransitive;

    /**
     * Local repo location for fetching dependencies to thaw. When this is
     * defined, the dependencies to thaw will be fetched from there and not from
     * the usual local repo. Using this setting is recommended.
     * 
     * @parameter
     */
    protected String thawDependencyRepositoryLocation;

    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * Used to create artifact instances.
     * 
     * @component role="org.apache.maven.artifact.factory.ArtifactFactory"
     * @required
     * @readonly
     */
    protected ArtifactFactory artifactFactory;

    /**
     * Location of the local repository.
     * 
     * @parameter expression="${localRepository}"
     * @readonly
     * @required
     */
    protected ArtifactRepository localRepository;

    /**
     * @component
     */
    protected MavenProjectHelper projectHelper;

    /**
     * @component
     * @required
     * @readonly
     */
    private DependencyTreeBuilder treeBuilder;

    /**
     * @component
     * @required
     * @readonly
     */
    private ArtifactMetadataSource artifactMetadataSource;

    /**
     * @component
     * @required
     * @readonly
     */
    private ArtifactCollector artifactCollector;

    @Override
    public void execute() throws MojoExecutionException
    {
        project.getAttachedArtifacts()
                .clear();

        ArtifactRepository dependencyRepo = null;
        if ( thawDependencyRepositoryLocation == null )
        {
            dependencyRepo = localRepository;
        }
        else
        {
            dependencyRepo = new MavenArtifactRepository();
            try
            {
                dependencyRepo.setUrl( new File(
                        thawDependencyRepositoryLocation ).toURI()
                        .toURL()
                        .toExternalForm() );
            }
            catch ( MalformedURLException mue )
            {
                throw new MojoExecutionException(
                        "Could not parse thaw dependency repository location: "
                                + thawDependencyRepositoryLocation, mue );
            }
        }

        for ( Artifact dependency : getDependencies() )
        {
            Artifact findArtifactsArtifact = artifactFactory.createArtifactWithClassifier(
                    dependency.getGroupId(), dependency.getArtifactId(),
                    dependency.getVersion(), "txt", "artifacts" );
            Artifact artifactsArtifact = localRepository.find( findArtifactsArtifact );
            File artifactsFile = artifactsArtifact.getFile();
            if ( !artifactsFile.exists() )
            {
                throw new MojoExecutionException(
                        "Could not find an artifact list for: " + dependency );
            }

            String[] lines = null;
            try
            {
                lines = FileUtils.fileRead( artifactsFile, "UTF-8" )
                        .split( "\n" );
            }
            catch ( IOException ioe )
            {
                throw new MojoExecutionException(
                        "Could not read artifact list for: " + dependency, ioe );
            }
            boolean pomWasAttached = false;
            for ( String artifactString : lines )
            {
                Artifact findArtifact = createArtifact( artifactString );
                findAndAttachExternalArtifact( findArtifact );
                if ( "pom".equals( findArtifact.getType() ) )
                {
                    pomWasAttached = true;
                }
            }
            if ( !pomWasAttached )
            {
                findAndAttachExternalArtifact( dependency.getGroupId(),
                        dependency.getArtifactId(), dependency.getVersion(),
                        "pom", null );
            }
        }
    }

    private void findAndAttachExternalArtifact( String groupId,
            String artifactId, String version, String type, String classifier )
            throws MojoExecutionException
    {
        Artifact findArtifact = createArtifact( groupId, artifactId, version,
                type, classifier );
        findAndAttachExternalArtifact( findArtifact );
    }

    private void findAndAttachExternalArtifact( Artifact findArtifact )
            throws MojoExecutionException
    {
        if ( findArtifact == null )
        {
            throw new MojoExecutionException( "Could not find artifact: "
                                              + findArtifact );
        }
        Artifact artifactToAttach = localRepository.find( findArtifact );
        if ( "pom".equals( artifactToAttach.getType() ) )
        {
            // point to a copy of the pom, otherwise it gets
            // corrupted as target and source are the same.
            // doesn't seem to happen to other artifacts.
            String fileName = artifactToAttach.getFile()
                    .getName();
            File destination = new File( new File( project.getBuild()
                    .getDirectory() ), fileName );
            try
            {
                FileUtils.copyFileIfModified( artifactToAttach.getFile(),
                        destination );
            }
            catch ( IOException e )
            {
                e.printStackTrace();
            }
            artifactToAttach.setFile( destination );
        }
        project.addAttachedArtifact( artifactToAttach );
        getLog().info( "Attached: " + artifactToAttach );
    }

    private Artifact createArtifact( String groupId, String artifactId,
            String version, String type, String classifier )
    {
        return artifactFactory.createArtifactWithClassifier( groupId,
                artifactId, version, type, classifier );
    }

    private Artifact createArtifact( String coords )
            throws MojoExecutionException
    {
        String[] strings = coords.split( ":" );
        if ( strings.length < 4 || strings.length > 5 )
        {
            throw new MojoExecutionException( "Can not parse coordinates: "
                                              + coords );
        }
        String groupId = strings[0];
        String artifactId = strings[1];
        String type = strings[2];
        String version = null;
        String classifier = null;
        if ( strings.length == 5 )
        {
            version = strings[4];
            classifier = strings[3];
        }
        else if ( strings.length == 4 )
        {
            version = strings[3];
        }
        return createArtifact( groupId, artifactId, version, type, classifier );
    }

    private Set<Artifact> getDependencies() throws MojoExecutionException
    {

        AndArtifactFilter filters = new AndArtifactFilter();
        if ( includes != null )
        {
            filters.add( new StrictPatternIncludesArtifactFilter( includes ) );
        }
        if ( excludes != null )
        {
            filters.add( new StrictPatternExcludesArtifactFilter( excludes ) );
        }

        Set<Artifact> artifacts = null;

        if ( excludeTransitive )
        {
            artifacts = getFilteredDirectDependencies( filters );
        }
        else
        {
            artifacts = getFilteredTransitiveDependencies( filters );
        }

        return artifacts;
    }

    private Set<Artifact> getFilteredTransitiveDependencies(
            ArtifactFilter filters ) throws MojoExecutionException
    {
        HashSet<Artifact> artifacts = new HashSet<Artifact>();
        DependencyNode rootNode = null;
        try
        {
            rootNode = treeBuilder.buildDependencyTree( project,
                    localRepository, artifactFactory, artifactMetadataSource,
                    filters, artifactCollector );
        }
        catch ( DependencyTreeBuilderException dtbe )
        {
            throw new MojoExecutionException(
                    "Failed to traverse dependencies.", dtbe );
        }

        CollectingDependencyNodeVisitor visitor = new CollectingDependencyNodeVisitor();

        rootNode.accept( visitor );

        List<DependencyNode> nodes = visitor.getNodes();
        for ( DependencyNode dependencyNode : nodes )
        {
            int state = dependencyNode.getState();
            if ( state == DependencyNode.INCLUDED )
            {
                artifacts.add( dependencyNode.getArtifact() );
            }
        }
        // remove this project, which is the root node of the tree
        artifacts.remove( project.getArtifact() );
        return artifacts;
    }

    private Set<Artifact> getFilteredDirectDependencies( ArtifactFilter filter )
    {
        Set<Artifact> directDependencies = project.getDependencyArtifacts();
        HashSet<Artifact> artifacts = new HashSet<Artifact>();
        for ( Artifact artifact : directDependencies )
        {
            if ( filter.include( artifact ) )
            {
                artifacts.add( artifact );
            }
        }
        return artifacts;
    }
}
