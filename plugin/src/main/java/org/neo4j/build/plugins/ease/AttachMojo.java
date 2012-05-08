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
import java.net.MalformedURLException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

/**
 * Goal which attached (copies of) artifacts for install or deploy.
 * 
 * @goal attach
 * @phase verify
 * @requiresProject true
 * @threadSafe true
 */
public class AttachMojo extends AbstractMojo
{
    /**
     * File system location of artifact list.
     * 
     * @parameter expression="${artifactListLocation}"
     * @required
     */
    private String artifactListLocation;

    /**
     * File system location of artifact repository to fetch artifacts from. It
     * is not allowed to point artifactRepositoryLocation to the location of the
     * local repository.
     * 
     * @parameter expression="${artifactRepositoryLocation}"
     */
    private String artifactRepositoryLocation;

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
     * Is either the local repo or a separate artifact repo in the file system.
     */
    private ArtifactRepository artifactRepository = null;

    @Override
    public void execute() throws MojoExecutionException
    {
        project.getAttachedArtifacts()
                .clear();

        String[] lines = null;
        try
        {
            lines = FileUtils.fileRead( artifactListLocation, "UTF-8" )
                    .split( "\n" );
        }
        catch ( IOException ioe )
        {
            throw new MojoExecutionException(
                    "Could not read artifact list from: "
                            + artifactListLocation );
        }

        artifactRepository = setupArtifactRepository( localRepository,
                artifactRepositoryLocation );

        getLog().info(
                "Loading artifacts from repository at: "
                        + artifactRepository.getBasedir() );

        for ( String artifactString : lines )
        {
            Artifact findArtifact = createArtifact( artifactString );
            if ( findArtifact == null )
            {
                throw new MojoExecutionException(
                        "Could not create artifact from coordinates: "
                                + artifactString );
            }
            findAndAttachExternalArtifact( findArtifact, artifactRepository );
        }
    }

    private static ArtifactRepository setupArtifactRepository(
            ArtifactRepository localRepo, String separateRepoLocation )
            throws MojoExecutionException
    {
        if ( separateRepoLocation != null )
        {
            String url;
            File artifactRepositoryDirectory = FileUtils.getFile( separateRepoLocation );
            try
            {
                url = artifactRepositoryDirectory.toURI()
                        .toURL()
                        .toExternalForm();
            }
            catch ( MalformedURLException mue )
            {
                throw new MojoExecutionException(
                        "Could not parse repository location: "
                                + separateRepoLocation, mue );
            }

            if ( !artifactRepositoryDirectory.exists() )
            {
                throw new MojoExecutionException(
                        "The repository location does not exist: "
                                + separateRepoLocation );
            }

            String id = "ease-source-repo";
            DefaultRepositoryLayout layout = new DefaultRepositoryLayout();
            ArtifactRepositoryPolicy snapshotsPolicy = new ArtifactRepositoryPolicy(
                    false, ArtifactRepositoryPolicy.UPDATE_POLICY_NEVER,
                    ArtifactRepositoryPolicy.CHECKSUM_POLICY_FAIL );
            ArtifactRepositoryPolicy releasesPolicy = new ArtifactRepositoryPolicy(
                    true, ArtifactRepositoryPolicy.UPDATE_POLICY_NEVER,
                    ArtifactRepositoryPolicy.CHECKSUM_POLICY_FAIL );
            ArtifactRepository separateArtifactRepository = new MavenArtifactRepository(
                    id, url, layout, snapshotsPolicy, releasesPolicy );
            if ( separateArtifactRepository.getBasedir()
                    .equals( localRepo.getBasedir() ) )
            {
                throw new MojoExecutionException(
                        "It is not allowed to point artifactRepositoryLocation to the location of the local repository." );
            }
            return separateArtifactRepository;
        }
        else
        {
            return localRepo;
        }
    }

    private void findAndAttachExternalArtifact( Artifact findArtifact,
            ArtifactRepository repository ) throws MojoExecutionException
    {
        Artifact artifactToAttach = repository.find( findArtifact );
        if ( !artifactToAttach.getFile()
                .exists() )
        {
            throw new MojoExecutionException( "Missing artifact file: "
                                              + findArtifact.getFile() );
        }

        if ( repository == localRepository )
        {
            String fileName = artifactToAttach.getFile()
                    .getName();
            File destination = new File( new File( project.getBuild()
                    .getDirectory() ), fileName );
            try
            {
                FileUtils.copyFileIfModified( artifactToAttach.getFile(),
                        destination );
            }
            catch ( IOException ioe )
            {
                throw new MojoExecutionException( "Could not copy file: "
                                                  + fileName, ioe );
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
}
