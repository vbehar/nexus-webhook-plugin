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

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.index.artifact.Gav;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.restlet.Context;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sonatype.nexus.plugins.webhook.ArtifactStoredEvent.Repository;
import org.sonatype.plexus.rest.resource.AbstractPlexusResource;
import org.sonatype.plexus.rest.resource.PathProtectionDescriptor;
import org.sonatype.plexus.rest.resource.PlexusResource;

/**
 * HTTP resource for sending fake events - use it to test your webhook handlers !<br>
 * Send HTTP (GET or POST) requests to "NEXUS_HOST/service/local/webhooks/fakeEvent" with the following parameters :<br>
 * <ul>
 * <li><strong>r</strong> : ID of the repository</li>
 * <li><strong>g</strong> : groupId of the artifact</li>
 * <li><strong>a</strong> : artifactId</li>
 * <li><strong>v</strong> : version of the artifact</li>
 * <li><strong>c</strong> : classifier of the artifact (sources, javadoc, etc)</li>
 * <li><strong>e</strong> : extension of the artifact (pom, jar, war, etc)</li>
 * </ul>
 * <br>
 * Example : <code>NEXUS_HOST/service/local/webhooks/fakeEvent?r=releases&g=com.example&a=webapp&v=2.1.0&c=&e=war</code> <br>
 * The plugin will then react as if a new artifact has been uploaded to Nexus, and send HTTP POST requests to the
 * registered webhook urls that matches the given parameters.<br>
 * The HTTP response will be "application/json" with the JSON POSTed to the webhook urls.
 * 
 * @author Vincent Behar
 */
@Component(role = PlexusResource.class, hint = "webHookFakeEvent")
public class WebHookFakeEvent extends AbstractPlexusResource {

    @Requirement
    private WebHookNotifier webHookNotifier;

    @Override
    public String getResourceUri() {
        return "/webhooks/fakeEvent";
    }

    @Override
    public Object get(Context context, Request request, Response response, Variant variant) throws ResourceException {
        // retrieve parameters (r, g, a, v, c, e)
        Form form = request.getResourceRef().getQueryAsForm();

        boolean snapshot = StringUtils.contains(form.getFirstValue("v"), "-SNAPSHOT");
        StringBuilder name = new StringBuilder().append(form.getFirstValue("a"))
                                                .append("-")
                                                .append(form.getFirstValue("v"));
        if (form.getFirstValue("c") != null) {
            name.append("-").append(form.getFirstValue("c"));
        }
        name.append(".").append(form.getFirstValue("e"));

        Repository repository = new Repository(form.getFirstValue("r"), form.getFirstValue("r"));
        Gav artifact = new Gav(form.getFirstValue("g"),
                               form.getFirstValue("a"),
                               form.getFirstValue("v"),
                               form.getFirstValue("c"),
                               form.getFirstValue("e"),
                               snapshot ? 42 : null,
                               snapshot ? new Date().getTime() : null,
                               name.toString(),
                               false,
                               null,
                               false,
                               null);

        ArtifactStoredEvent event = new ArtifactStoredEvent();
        event.setRepository(repository);
        event.setArtifact(artifact);
        event.setTimestamp(new Date().getTime());
        event.setUser("fake");

        webHookNotifier.notify(event);

        return event;
    }

    @Override
    public Object post(Context context, Request request, Response response, Object payload) throws ResourceException {
        return get(context, request, response, new Variant(MediaType.ALL));
    }

    @Override
    public PathProtectionDescriptor getResourceProtection() {
        // should be new PathProtectionDescriptor(getResourceUri(), "anon");
        // BUT https://issues.sonatype.org/browse/NEXUS-3951
        return new PathProtectionDescriptor(getResourceUri(), "authcBasic");
    }

    @Override
    public List<Variant> getVariants() {
        return Arrays.asList(new Variant(MediaType.APPLICATION_JSON));
    }

    @Override
    public Object getPayloadInstance() {
        return null;
    }

}
