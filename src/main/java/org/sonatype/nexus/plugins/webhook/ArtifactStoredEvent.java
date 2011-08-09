/*
 * Copyright 2011 Vincent Behar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sonatype.nexus.plugins.webhook;

import java.io.Serializable;
import org.apache.maven.index.artifact.Gav;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents an event triggered when a (Maven) artifact is stored in Nexus.<br>
 * This event is sent to webhook listeners as JSON.
 * 
 * @author Vincent Behar
 */
public class ArtifactStoredEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** timestamp of the event (number of milliseconds since January 1, 1970, 00:00:00 GMT) */
    private Long timestamp;

    /** name of the user (in nexus) that is responsible for uploading the artifact */
    private String user;

    /** the affected repository */
    private Repository repository;

    /** details about the uploaded artifact */
    private Gav artifact;

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public Gav getArtifact() {
        return artifact;
    }

    public void setArtifact(Gav artifact) {
        this.artifact = artifact;
    }

    /**
     * @return a JSON representation of the event
     * @throws JSONException
     */
    public String toJson() throws JSONException {
        JSONObject jsonRepository = new JSONObject(repository);
        jsonRepository.remove("class");
        JSONObject jsonArtifact = new JSONObject(artifact);
        jsonArtifact.remove("class");
        return new JSONObject().put("timestamp", timestamp)
                               .put("user", user)
                               .put("repository", jsonRepository)
                               .put("artifact", jsonArtifact)
                               .toString();
    }

    /**
     * {@link Gav} does not provides a meaningful toString() implementation...
     * 
     * @param gav
     * @return meaningful toString() implementation
     */
    private String gavToString(Gav gav) {
        return gav == null ? "null" : "Gav [groupId=" + gav.getGroupId() + ", artifactId=" + gav.getArtifactId()
                                      + ", baseVersion=" + gav.getBaseVersion() + ", version=" + gav.getVersion()
                                      + ", classifier=" + gav.getClassifier() + ", extension=" + gav.getExtension()
                                      + ", name=" + gav.getName() + ", snapshot=" + gav.isSnapshot()
                                      + ", snapshotBuildNumber=" + gav.getSnapshotBuildNumber()
                                      + ", snapshotTimeStamp=" + gav.getSnapshotTimeStamp() + ", hash=" + gav.isHash()
                                      + ", hashType=" + gav.getHashType() + ", signature=" + gav.isSignature()
                                      + ", signatureType=" + gav.getSignatureType() + "]";
    }

    @Override
    public String toString() {
        return "ArtifactStoredEvent [artifact=" + gavToString(artifact) + ", repository=" + repository + ", timestamp="
               + timestamp + ", user=" + user + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((artifact == null) ? 0 : artifact.hashCode());
        result = prime * result + ((repository == null) ? 0 : repository.hashCode());
        result = prime * result + ((timestamp == null) ? 0 : timestamp.hashCode());
        result = prime * result + ((user == null) ? 0 : user.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ArtifactStoredEvent other = (ArtifactStoredEvent) obj;
        if (artifact == null) {
            if (other.artifact != null)
                return false;
        } else if (!artifact.equals(other.artifact))
            return false;
        if (repository == null) {
            if (other.repository != null)
                return false;
        } else if (!repository.equals(other.repository))
            return false;
        if (timestamp == null) {
            if (other.timestamp != null)
                return false;
        } else if (!timestamp.equals(other.timestamp))
            return false;
        if (user == null) {
            if (other.user != null)
                return false;
        } else if (!user.equals(other.user))
            return false;
        return true;
    }

    public static class Repository implements Serializable {

        private static final long serialVersionUID = 1L;

        private String id;

        private String name;

        public Repository(String id, String name) {
            super();
            this.id = id;
            this.name = name;
        }

        public Repository() {
            super();
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "Repository [id=" + id + ", name=" + name + "]";
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Repository other = (Repository) obj;
            if (id == null) {
                if (other.id != null)
                    return false;
            } else if (!id.equals(other.id))
                return false;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            return true;
        }

    }

}
