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

package org.wso2.carbon.identity.moesif.handler.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.carbon.event.stream.core.EventStreamService;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.carbon.identity.governance.IdentityGovernanceService;
import org.wso2.carbon.identity.moesif.handler.MoesifFlowDataPublishHandler;
import org.wso2.carbon.identity.moesif.handler.MoesifOAuthTokenIssuanceDataPublishHandler;
import org.wso2.carbon.identity.moesif.handler.MoesifOrgSwitchDataPublishHandler;
import org.wso2.carbon.identity.moesif.handler.MoesifRegistrationDataPublishHandler;
import org.wso2.carbon.identity.moesif.handler.MoesifSessionDataPublisherHandler;
import org.wso2.carbon.identity.moesif.handler.MoesifUserAuthenticationDataPublishHandler;
import org.wso2.carbon.identity.organization.management.service.OrganizationManager;

/**
 * OSGi service component for Moesif event handlers.
 */
@Component(
        name = "identity.moesif.handler",
        immediate = true
)
public class MoesifHandlerServiceComponent {

    private static final Log log = LogFactory.getLog(MoesifHandlerServiceComponent.class);

    @Activate
    protected void activate(ComponentContext context) {

        try {
            BundleContext bundleContext = context.getBundleContext();
            bundleContext.registerService(AbstractEventHandler.class,
                    new MoesifUserAuthenticationDataPublishHandler(), null);
            bundleContext.registerService(AbstractEventHandler.class,
                    new MoesifFlowDataPublishHandler(), null);
            bundleContext.registerService(AbstractEventHandler.class,
                    new MoesifRegistrationDataPublishHandler(), null);
            bundleContext.registerService(AbstractEventHandler.class,
                    new MoesifOrgSwitchDataPublishHandler(), null);
            bundleContext.registerService(AbstractEventHandler.class,
                    new MoesifSessionDataPublisherHandler(), null);
            bundleContext.registerService(AbstractEventHandler.class,
                    new MoesifOAuthTokenIssuanceDataPublishHandler(), null);

            if (log.isDebugEnabled()) {
                log.debug("identity.moesif.handler bundle activated.");
            }
        } catch (Exception e) {
            log.error("Error activating identity.moesif.handler bundle.", e);
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {

        if (log.isDebugEnabled()) {
            log.debug("identity.moesif.handler bundle deactivated.");
        }
    }

    @Reference(
            name = "EventStreamService",
            service = EventStreamService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetEventStreamService"
    )
    protected void setEventStreamService(EventStreamService eventStreamService) {

        MoesifHandlerDataHolder.getInstance().setPublisherService(eventStreamService);
    }

    protected void unsetEventStreamService(EventStreamService eventStreamService) {

        MoesifHandlerDataHolder.getInstance().setPublisherService(null);
    }

    @Reference(
            name = "identity.organization.management.component",
            service = OrganizationManager.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetOrganizationManager"
    )
    protected void setOrganizationManager(OrganizationManager organizationManager) {

        MoesifHandlerDataHolder.getInstance().setOrganizationManager(organizationManager);
    }

    protected void unsetOrganizationManager(OrganizationManager organizationManager) {

        MoesifHandlerDataHolder.getInstance().setOrganizationManager(null);
    }

    @Reference(
            name = "org.wso2.carbon.identity.governance.IdentityGovernanceService",
            service = IdentityGovernanceService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetIdentityGovernanceService"
    )
    protected void setIdentityGovernanceService(IdentityGovernanceService identityGovernanceService) {

        MoesifHandlerDataHolder.getInstance().setIdentityGovernanceService(identityGovernanceService);
    }

    protected void unsetIdentityGovernanceService(IdentityGovernanceService identityGovernanceService) {

        MoesifHandlerDataHolder.getInstance().setIdentityGovernanceService(null);
    }
}
