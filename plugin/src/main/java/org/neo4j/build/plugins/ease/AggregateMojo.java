/**
 * Licensed to Neo Technology under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Neo Technology licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.neo4j.build.plugins.ease;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
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
 * Aggregates multiple artifact lists into a single list and attaches it to the
 * project.
 * 
 * The lists to aggregate are found by traversing the dependency tree of the
 * project, and apply filters on the result. If no configuration is given, all
 * dependencies (including transitive) will be included.
 * 
 * Note: Every included dependency must have an -artifacts.txt file, or the
 * plugin will fail the build.
 * 
 * @goal aggregate
 * @phase verify
 * @requiresProject true
 * @threadSafe true
 */
public class AggregateMojo extends AbstractMojo
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
     * If we should exclude transitive dependencies.
     * 
     * @parameter expression="${excludeTransitive}" default-value="false"
     */
    protected boolean excludeTransitive;

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
        SortedSet<String> aggregate = new TreeSet<String>();
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

            String artifactList = null;
            try
            {
                artifactList = FileUtils.fileRead( artifactsFile, "UTF-8" );
            }
            catch ( IOException ioe )
            {
                throw new MojoExecutionException(
                        "Could not read artifact list for: " + dependency, ioe );
            }
            for ( String line : artifactList.split( "\n" ) )
            {
                aggregate.add( line );
            }
        }
        StringBuilder builder = new StringBuilder( aggregate.size() * 64 );
        for ( String artifactLine : aggregate )
        {
            builder.append( artifactLine )
                    .append( '\n' );
        }
        EaseHelper.writeAndAttachArtifactList( builder, project, projectHelper,
                getLog() );
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
            ArtifactFilter filter ) throws MojoExecutionException
    {
        HashSet<Artifact> artifacts = new HashSet<Artifact>();
        DependencyNode rootNode = null;
        try
        {
            rootNode = treeBuilder.buildDependencyTree( project,
                    localRepository, artifactFactory, artifactMetadataSource,
                    null, artifactCollector );
        }
        catch ( DependencyTreeBuilderException dtbe )
        {
            throw new MojoExecutionException(
                    "Failed to traverse dependencies.", dtbe );
        }

        CollectingDependencyNodeVisitor visitor = new CollectingDependencyNodeVisitor();

        /*
        // filter during dependency traversal
        FilteringDependencyNodeVisitor filteringVisitor = new FilteringDependencyNodeVisitor( visitor,
                new ArtifactDependencyNodeFilter( filter ) );

        rootNode.accept( filteringVisitor );*/

        rootNode.accept( visitor );

        List<DependencyNode> nodes = visitor.getNodes();
        for ( DependencyNode dependencyNode : nodes )
        {
            int state = dependencyNode.getState();
            if ( state == DependencyNode.INCLUDED
                 && filter.include( dependencyNode.getArtifact() ) )
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
