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
import java.util.Iterator;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.util.FileUtils;

/**
 * Goal which records artifacts.
 * 
 * @goal freeze
 * @requiresProject true
 * @phase verify
 * @threadSafe true
 */
public class FreezeMojo extends AbstractMojo
{
    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * @parameter default-value="${project.attachedArtifacts}
     * @required
     * @readonly
     */
    private List attachedArtifacts;

    /**
     * @component
     */
    protected MavenProjectHelper projectHelper;

    @Override
    public void execute() throws MojoExecutionException
    {
        StringBuilder builder = new StringBuilder( 512 );
        Artifact artifact = project.getArtifact();
        addArtifactCoordinates( builder, artifact );
        for ( Iterator i = attachedArtifacts.iterator(); i.hasNext(); )
        {
            Artifact attached = (Artifact) i.next();
            addArtifactCoordinates( builder, attached );
        }
        String buildDir = project.getBuild()
                .getDirectory();
        String destFile = buildDir + File.separator + project.getArtifactId()
                          + "-" + project.getVersion() + "-artifacts.txt";
        try
        {
            if ( FileUtils.fileExists( destFile ) )
            {
                FileUtils.fileDelete( destFile );
            }
            if ( !FileUtils.fileExists( buildDir ) )
            {
                FileUtils.mkdir( buildDir );
            }
            FileUtils.fileWrite( destFile, "UTF-8", builder.toString() );
        }
        catch ( IOException ioe )
        {
            throw new MojoExecutionException( "Could not write artifact list.",
                    ioe );
        }
        projectHelper.attachArtifact( project, "txt", "artifacts",
                FileUtils.getFile( destFile ) );
        getLog().info( "Successfully attached artifact list to the project." );
    }

    private void addArtifactCoordinates( StringBuilder builder,
            Artifact attached )
    {
        builder.append( attached.getGroupId() )
                .append( ':' )
                .append( attached.getArtifactId() )
                .append( ':' );
        // workaround for missing artifact files in pom projects
        if ( "pom".equals( attached.getType() ) )
        {
            builder.append( "pom" );
        }
        else
        {
            builder.append( FileUtils.extension( attached.getFile()
                    .getName() ) );
        }
        builder.append( ':' );
        if ( attached.hasClassifier() )
        {
            builder.append( attached.getClassifier() )
                    .append( ':' );
        }
        builder.append( attached.getVersion() );
        builder.append( '\n' );
    }
}
