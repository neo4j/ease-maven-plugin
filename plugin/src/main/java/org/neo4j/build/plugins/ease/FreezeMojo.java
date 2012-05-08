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
        boolean pomWasAdded = "pom".equals( artifact.getType() );
        for ( Iterator i = attachedArtifacts.iterator(); i.hasNext(); )
        {
            Artifact attached = (Artifact) i.next();
            addArtifactCoordinates( builder, attached );
            pomWasAdded = pomWasAdded || "pom".equals( artifact.getType() );
        }
        if ( !pomWasAdded )
        {
            addArtifactCoordinates( builder, project.getGroupId(),
                    project.getArtifactId(), "pom", project.getVersion(), null );
        }

        EaseHelper.writeAndAttachArtifactList( builder, project, projectHelper,
                getLog() );
    }

    private void addArtifactCoordinates( StringBuilder builder,
            Artifact attached )
    {
        String groupId = attached.getGroupId();
        String artifactId = attached.getArtifactId();
        // workaround for missing artifact files in pom projects
        String type = "pom";
        if ( !"pom".equals( attached.getType() ) )
        {
            type = FileUtils.extension( attached.getFile()
                    .getName() );
        }
        String version = attached.getVersion();
        String classifier = null;
        if ( attached.hasClassifier() )
        {
            classifier = attached.getClassifier();
        }
        addArtifactCoordinates( builder, groupId, artifactId, type, version,
                classifier );
    }

    private void addArtifactCoordinates( StringBuilder builder, String groupId,
            String artifactId, String type, String version, String classifier )
    {
        builder.append( groupId )
                .append( ':' )
                .append( artifactId )
                .append( ':' )
                .append( type )
                .append( ':' );
        if ( classifier != null )
        {
            builder.append( classifier )
                    .append( ':' );
        }
        builder.append( version )
                .append( '\n' );
    }
}
