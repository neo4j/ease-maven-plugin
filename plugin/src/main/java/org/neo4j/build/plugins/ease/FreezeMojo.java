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

import java.util.Iterator;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.util.FileUtils;

/**
 * Lists all attached artifacts and attaches that list to to the project.
 * 
 * Attached artifacts are the pom.xml file, default jar file, source jar etc. --
 * whatever has been attached to the project. The list is attached as the
 * -artifacts.txt artifact and will be included when performing install or
 * deploy.
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
     * If we should ignore artifacts that do not have a file assigned.
     *
     * @parameter expression="${ignoreEmptyArtifacts}" default-value="false"
     * @readonly
     */
    protected boolean ignoreEmptyArtifacts;

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
            if ( ignoreEmptyArtifacts && (attached.getFile() == null ||
                                    ! attached.getFile().isFile()) )
            {
                getLog().debug( "Artifact has no file assigned, ignored: " + attached );
                return;
            }

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
