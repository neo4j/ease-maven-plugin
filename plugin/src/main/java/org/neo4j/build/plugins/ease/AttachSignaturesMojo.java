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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

/**
 * Attaches all signatures of signed artifacts to the project. Will fail on
 * missing signatures.
 * 
 * @goal attachsignatures
 * @requiresProject true
 * @phase verify
 * @threadSafe true
 */
public class AttachSignaturesMojo extends AbstractMojo
{
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

    private final Set<String> attachedIds = new HashSet<String>();

    @Override
    public void execute() throws MojoExecutionException
    {
        List<Artifact> artifacts = new ArrayList<Artifact>(
                project.getAttachedArtifacts() );
        project.getAttachedArtifacts()
                .clear();
        for ( Artifact artifact : artifacts )
        {
            String type = artifact.getType();
            if ( artifact.getArtifactId()
                    .equals( project.getArtifactId() ) && artifact.getGroupId()
                    .equals( project.getGroupId() ) )
            {
                // only add back the project artifacts we want
                if ( type.equals( "pom.asc" ) || type.equals( "pom" ) )
                {
                    attach( artifact );
                }
            }
            else
            {
                // re-attach and check if there's a signature to attach as well
                attach( artifact );
                if ( !type.endsWith( ".asc" ) )
                {
                    File artifactFile = artifact.getFile();
                    String signaturePath = artifactFile.getAbsolutePath()
                                           + ".asc";
                    File signatureFile = FileUtils.getFile( signaturePath );
                    if ( signatureFile.exists() )
                    {
                        String classifier = null;
                        if ( artifact.hasClassifier() )
                        {
                            classifier = artifact.getClassifier();
                        }
                        Artifact signatureArtifact = artifactFactory.createArtifactWithClassifier(
                                artifact.getGroupId(),
                                artifact.getArtifactId(),
                                artifact.getVersion(), type + ".asc",
                                classifier );
                        signatureArtifact.setFile( signatureFile );
                        attach( signatureArtifact );
                    }
                    else
                    {
                        throw new MojoExecutionException(
                                "Missing signature for artifact: " + artifact );
                    }
                }
            }
        }
    }

    private void attach( Artifact artifact )
    {
        String id = artifact.getId();
        if ( !attachedIds.contains( id ) )
        {
            project.addAttachedArtifact( artifact );
            attachedIds.add( id );
            getLog().info( "Attached: " + id );
        }
    }
}
