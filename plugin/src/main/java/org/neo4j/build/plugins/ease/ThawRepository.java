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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.repository.LocalArtifactRepository;

public class ThawRepository extends LocalArtifactRepository
{
    public ThawRepository( String url )
    {
        this.setUrl( url );
        this.setLayout( new DefaultRepositoryLayout() );
    }

    @Override
    public Artifact find( Artifact artifact )
    {
        File artifactFile = new File( getBasedir(), pathOf( artifact ) );
        artifact.setFile( artifactFile );
        return artifact;
    }

    @Override
    public boolean hasLocalMetadata()
    {
        return false;
    }
}
