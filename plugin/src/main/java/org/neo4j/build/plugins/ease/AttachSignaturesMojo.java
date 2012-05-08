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
import java.util.Iterator;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

/**
 * Attaches all unattached signatures of signed artifacts in the build directory
 * with the project.
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
     * @parameter default-value="${project.attachedArtifacts}
     * @required
     * @readonly
     */
    private List attachedArtifacts;

    /**
     * Used to create artifact instances.
     * 
     * @component role="org.apache.maven.artifact.factory.ArtifactFactory"
     * @required
     * @readonly
     */
    protected ArtifactFactory artifactFactory;

    @Override
    public void execute() throws MojoExecutionException
    {
        List<Artifact> artifacts = new ArrayList<Artifact>();
        for ( Iterator i = attachedArtifacts.iterator(); i.hasNext(); )
        {
            Artifact artifact = (Artifact) i.next();
            artifacts.add( artifact );
        }
        // simply attach the signature of all non-signature artifacts
        for ( Artifact artifact : artifacts )
        {
            String type = artifact.getType();
            if ( !type.endsWith( ".asc" ) )
            {
                File artifactFile = artifact.getFile();
                String signaturePath = artifactFile.getAbsolutePath() + ".asc";
                File signatureFile = FileUtils.getFile( signaturePath );
                if ( signatureFile.exists() )
                {
                    String classifier = null;
                    if ( artifact.hasClassifier() )
                    {
                        classifier = artifact.getClassifier();
                    }
                    Artifact signatureArtifact = artifactFactory.createArtifactWithClassifier(
                            artifact.getGroupId(), artifact.getArtifactId(),
                            artifact.getVersion(), type + ".asc", classifier );
                    signatureArtifact.setFile( signatureFile );
                    project.addAttachedArtifact( signatureArtifact );
                    getLog().info(
                            "Attached signature: " + signatureArtifact.getId() );
                }
            }
        }
    }
}
