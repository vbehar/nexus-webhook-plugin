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

import org.apache.maven.index.artifact.Gav;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.sonatype.nexus.plugins.webhook.ArtifactStoredEvent.Repository;
import org.sonatype.nexus.proxy.events.EventInspector;
import org.sonatype.nexus.proxy.events.NexusStartedEvent;
import org.sonatype.nexus.proxy.events.RepositoryItemEventStore;
import org.sonatype.nexus.proxy.maven.MavenRepository;
import org.sonatype.plexus.appevents.Event;

/**
 * Nexus {@link EventInspector} implementation that loads the {@link WebHookPlugin} configuration at startup, and notify
 * the registered webHook listeners when a new (Maven) artifact is stored in Nexus.
 * 
 * @author Vincent Behar
 */
@Component(role = EventInspector.class, hint = "webhookEventInspector")
public class WebHookEventInspector implements EventInspector {

    @Requirement
    private WebHookPlugin webHookPlugin;

    @Requirement
    private WebHookNotifier webHookNotifier;

    public boolean accepts(Event<?> evt) {
        return true;
    }

    public void inspect(Event<?> evt) {
        if (evt instanceof NexusStartedEvent) {
            webHookPlugin.reloadConfigurationQuietly();
        }

        if (evt instanceof RepositoryItemEventStore) {
            RepositoryItemEventStore e = (RepositoryItemEventStore) evt;
            if (e.getRepository() instanceof MavenRepository) {
                MavenRepository repo = (MavenRepository) e.getRepository();
                String path = e.getItemUid().getPath();
                Gav gav = repo.getGavCalculator().pathToGav(path);
                if (gav != null && !gav.isSignature() && !gav.isHash()) {
                    ArtifactStoredEvent event = new ArtifactStoredEvent();
                    event.setRepository(new Repository(repo.getId(), repo.getName()));
                    event.setArtifact(gav);
                    event.setTimestamp(e.getEventDate().getTime());
                    event.setUser(String.valueOf(e.getItemContext().get("request.user")));
                    webHookNotifier.notify(event);
                }
            }
        }
    }

}
