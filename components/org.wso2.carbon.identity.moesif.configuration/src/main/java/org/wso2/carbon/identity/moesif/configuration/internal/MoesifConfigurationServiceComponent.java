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

package org.wso2.carbon.identity.moesif.configuration.internal;

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
import org.wso2.carbon.identity.configuration.mgt.core.ConfigurationManager;
import org.wso2.carbon.identity.governance.IdentityGovernanceService;
import org.wso2.carbon.identity.governance.common.IdentityConnectorConfig;
import org.wso2.carbon.identity.moesif.configuration.MoesifConfigurationManagementService;
import org.wso2.carbon.identity.moesif.configuration.MoesifConfigurationManagementServiceImpl;
import org.wso2.carbon.identity.moesif.configuration.MoesifGovernanceConnectorConfig;
import org.wso2.carbon.identity.secret.mgt.core.SecretManager;
import org.wso2.carbon.identity.secret.mgt.core.SecretResolveManager;
import org.wso2.carbon.identity.tenant.resource.manager.core.ResourceManager;

/**
 * OSGi service component for Moesif configuration management.
 */
@Component(
        name = "identity.moesif.configuration",
        immediate = true
)
public class MoesifConfigurationServiceComponent {

    private static final Log log = LogFactory.getLog(MoesifConfigurationServiceComponent.class);

    @Activate
    protected void activate(ComponentContext context) {

        try {
            BundleContext bundleContext = context.getBundleContext();
            bundleContext.registerService(MoesifConfigurationManagementService.class,
                    new MoesifConfigurationManagementServiceImpl(), null);
            bundleContext.registerService(IdentityConnectorConfig.class.getName(),
                    new MoesifGovernanceConnectorConfig(), null);

            if (log.isDebugEnabled()) {
                log.debug("identity.moesif.configuration bundle activated.");
            }
        } catch (Exception e) {
            log.error("Error activating identity.moesif.configuration bundle.", e);
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {

        if (log.isDebugEnabled()) {
            log.debug("identity.moesif.configuration bundle deactivated.");
        }
    }

    @Reference(
            name = "ConfigurationManager",
            service = ConfigurationManager.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetConfigurationManager"
    )
    protected void setConfigurationManager(ConfigurationManager configurationManager) {

        MoesifConfigurationDataHolder.getInstance().setConfigurationManager(configurationManager);
    }

    protected void unsetConfigurationManager(ConfigurationManager configurationManager) {

        MoesifConfigurationDataHolder.getInstance().setConfigurationManager(null);
    }

    @Reference(
            name = "resource.manager",
            service = ResourceManager.class,
            cardinality = ReferenceCardinality.OPTIONAL,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetResourceManager"
    )
    protected void setResourceManager(ResourceManager resourceManager) {

        MoesifConfigurationDataHolder.getInstance().setResourceManager(resourceManager);
    }

    protected void unsetResourceManager(ResourceManager resourceManager) {

        MoesifConfigurationDataHolder.getInstance().setResourceManager(null);
    }

    @Reference(
            name = "org.wso2.carbon.identity.secret.mgt.core.SecretManager",
            service = SecretManager.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetSecretManager"
    )
    protected void setSecretManager(SecretManager secretManager) {

        MoesifConfigurationDataHolder.getInstance().setSecretManager(secretManager);
    }

    protected void unsetSecretManager(SecretManager secretManager) {

        MoesifConfigurationDataHolder.getInstance().setSecretManager(null);
    }

    @Reference(
            name = "org.wso2.carbon.identity.secret.mgt.core.SecretResolveManager",
            service = SecretResolveManager.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetSecretResolveManager"
    )
    protected void setSecretResolveManager(SecretResolveManager secretResolveManager) {

        MoesifConfigurationDataHolder.getInstance().setSecretResolveManager(secretResolveManager);
    }

    protected void unsetSecretResolveManager(SecretResolveManager secretResolveManager) {

        MoesifConfigurationDataHolder.getInstance().setSecretResolveManager(null);
    }

    @Reference(
            name = "org.wso2.carbon.identity.governance.IdentityGovernanceService",
            service = IdentityGovernanceService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetIdentityGovernanceService"
    )
    protected void setIdentityGovernanceService(IdentityGovernanceService identityGovernanceService) {

        MoesifConfigurationDataHolder.getInstance().setIdentityGovernanceService(identityGovernanceService);
    }

    protected void unsetIdentityGovernanceService(IdentityGovernanceService identityGovernanceService) {

        MoesifConfigurationDataHolder.getInstance().setIdentityGovernanceService(null);
    }
}
