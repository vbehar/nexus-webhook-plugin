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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.sonatype.plexus.rest.resource.AbstractPlexusResource;
import org.sonatype.plexus.rest.resource.PathProtectionDescriptor;
import org.sonatype.plexus.rest.resource.PlexusResource;

/**
 * Reload the plugin's configuration when an HTTP request (GET or POST) is made to
 * "NEXUS_HOST/service/local/webhooks/configuration/reload".<br>
 * In case of success, returns an HTTP code 200 with a "text/plain" response containing a success message.<br>
 * In case of error, returns an HTTP code 500 with an attached error message.
 * 
 * @author Vincent Behar
 */
@Component(role = PlexusResource.class, hint = "webhookConfigurationReloader")
public class WebHookConfigurationReloader extends AbstractPlexusResource {

    @Requirement
    private WebHookPlugin webHookPlugin;

    @Override
    public String getResourceUri() {
        return "/webhooks/configuration/reload";
    }

    @Override
    public Object get(Context context, Request request, Response response, Variant variant) throws ResourceException {
        try {
            webHookPlugin.reloadConfiguration();
            return "Nexus WebHook Plugin configuration has been successfully reloaded !";
        } catch (IllegalArgumentException e) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                                        "Failed to reload the Nexus WebHook Plugin configuration : " + e.getMessage(),
                                        e);
        } catch (IOException e) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL,
                                        "Failed to reload the Nexus WebHook Plugin configuration : " + e.getMessage(),
                                        e);
        }
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
        return Arrays.asList(new Variant(MediaType.TEXT_PLAIN));
    }

    @Override
    public Object getPayloadInstance() {
        return null;
    }

}
