/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.moesif.publisher.http.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.wso2.carbon.event.output.adapter.core.OutputEventAdapterFactory;
import org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterFactory;

/**
 * OSGi service component that registers the {@link MoesifHTTPEventAdapterFactory} with the runtime.
 *
 * <p>Registering the factory under the {@link OutputEventAdapterFactory} interface makes the
 * {@code moesif-http} adapter type available for event publisher XML configurations.
 */
@Component(
        name = "identity.moesif.publisher.http",
        immediate = true
)
public class MoesifHTTPPublisherServiceComponent {

    private static final Log LOG = LogFactory.getLog(MoesifHTTPPublisherServiceComponent.class);

    @Activate
    protected void activate(ComponentContext context) {

        try {
            BundleContext bundleContext = context.getBundleContext();
            bundleContext.registerService(
                    OutputEventAdapterFactory.class.getName(),
                    new MoesifHTTPEventAdapterFactory(),
                    null);
            LOG.debug("Moesif HTTP publisher service component activated successfully.");
        } catch (Exception e) {
            LOG.error("Error while activating Moesif HTTP publisher service component.", e);
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {

        LOG.debug("Moesif HTTP publisher service component deactivated.");
    }
}
