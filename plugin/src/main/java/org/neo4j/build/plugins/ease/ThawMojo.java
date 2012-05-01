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
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
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
     * GroupIds to attach to the project.
     * 
     * @parameter
     * @required
     */
    protected List<String> groupIds;

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
    protected ArtifactFactory artFactory;

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

    @Override
    public void execute() throws MojoExecutionException
    {
        project.getAttachedArtifacts()
                .clear();
        for ( Dependency dependency : project.getDependencies() )
        {
            if ( groupIds.contains( dependency.getGroupId() ) )
            {
                Artifact findArtifactsArtifact = artFactory.createArtifactWithClassifier(
                        dependency.getGroupId(), dependency.getArtifactId(),
                        dependency.getVersion(), "txt", "artifacts" );
                Artifact artifactsArtifact = localRepository.find( findArtifactsArtifact );
                File artifactsFile = artifactsArtifact.getFile();
                if ( !artifactsFile.exists() )
                {
                    continue;
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
                            "Could not read artifact list for: " + dependency,
                            ioe );
                }
                for ( String artifactString : lines )
                {
                    String[] strings = artifactString.split( ":" );
                    if ( strings.length < 4 || strings.length > 5 )
                    {
                        getLog().error(
                                "Can't parse artifact coordinates: "
                                        + artifactString );
                        continue;
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
                        if ( !"pom".equals( type ) )
                        {
                            // add the pom as well
                            findAndAttachExternalArtifact( groupId, artifactId,
                                    version, "pom", null );
                        }
                    }
                    findAndAttachExternalArtifact( groupId, artifactId,
                            version, type, classifier );
                }
            }
        }
    }

    private void findAndAttachExternalArtifact( String groupId,
            String artifactId, String version, String type, String classifier )
    {
        Artifact findArtifact = artFactory.createArtifactWithClassifier(
                groupId, artifactId, version, type, classifier );
        if ( findArtifact != null )
        {
            Artifact artifactToAttach = localRepository.find( findArtifact );
            if ( "pom".equals( type ) )
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
            System.out.println( "added: " + artifactToAttach );
        }
        else
        {
            getLog().error( "Could not find artifact: " + findArtifact );
        }
    }
}
