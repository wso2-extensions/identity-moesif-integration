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

package org.wso2.carbon.identity.moesif.configuration;

import org.wso2.carbon.identity.governance.IdentityGovernanceException;
import org.wso2.carbon.identity.governance.common.IdentityConnectorConfig;
import org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Governance connector configuration for the Moesif publisher.
 * Exposes a per-tenant {@code moesif.publisher.enabled} property that controls
 * whether Moesif event publishing is active for a given tenant (root organisation).
 *
 * <p>The connector is registered as an OSGi service so it appears in the IS
 * administration console under <em>Analytics &gt; Moesif Publisher</em>.</p>
 */
public class MoesifGovernanceConnectorConfig implements IdentityConnectorConfig {

    private static final String CONNECTOR_NAME = "moesif-publisher";
    private static final String FRIENDLY_NAME = "Moesif Publisher";
    private static final String CATEGORY = "Analytics";
    private static final String SUB_CATEGORY = "DEFAULT";

    @Override
    public String getName() {

        return CONNECTOR_NAME;
    }

    @Override
    public String getFriendlyName() {

        return FRIENDLY_NAME;
    }

    @Override
    public String getCategory() {

        return CATEGORY;
    }

    @Override
    public String getSubCategory() {

        return SUB_CATEGORY;
    }

    @Override
    public int getOrder() {

        return 0;
    }

    @Override
    public Map<String, String> getPropertyNameMapping() {

        Map<String, String> nameMapping = new HashMap<>();
        nameMapping.put(MoesifCommonConstants.MOESIF_AUTHENTICATION_PUBLISHER_ENABLED_PROPERTY,
                "Enable Authentication Publisher");
        nameMapping.put(MoesifCommonConstants.MOESIF_REGISTRATION_PUBLISHER_ENABLED_PROPERTY,
                "Enable Registration Publisher");
        nameMapping.put(MoesifCommonConstants.MOESIF_FLOW_PUBLISHER_ENABLED_PROPERTY,
                "Enable Flow Publisher");
        nameMapping.put(MoesifCommonConstants.MOESIF_OAUTH_TOKEN_PUBLISHER_ENABLED_PROPERTY,
                "Enable OAuth Token Publisher");
        return nameMapping;
    }

    @Override
    public Map<String, String> getPropertyDescriptionMapping() {

        Map<String, String> descriptionMapping = new HashMap<>();
        descriptionMapping.put(MoesifCommonConstants.MOESIF_AUTHENTICATION_PUBLISHER_ENABLED_PROPERTY,
                "Enable or disable Moesif login/authentication event publishing for this tenant.");
        descriptionMapping.put(MoesifCommonConstants.MOESIF_REGISTRATION_PUBLISHER_ENABLED_PROPERTY,
                "Enable or disable Moesif user registration event publishing for this tenant.");
        descriptionMapping.put(MoesifCommonConstants.MOESIF_FLOW_PUBLISHER_ENABLED_PROPERTY,
                "Enable or disable Moesif flow event publishing for this tenant.");
        descriptionMapping.put(MoesifCommonConstants.MOESIF_OAUTH_TOKEN_PUBLISHER_ENABLED_PROPERTY,
                "Enable or disable Moesif OAuth Token event publishing for this tenant.");
        return descriptionMapping;
    }

    @Override
    public String[] getPropertyNames() {

        return new String[]{
                MoesifCommonConstants.MOESIF_AUTHENTICATION_PUBLISHER_ENABLED_PROPERTY,
                MoesifCommonConstants.MOESIF_REGISTRATION_PUBLISHER_ENABLED_PROPERTY,
                MoesifCommonConstants.MOESIF_FLOW_PUBLISHER_ENABLED_PROPERTY,
                MoesifCommonConstants.MOESIF_OAUTH_TOKEN_PUBLISHER_ENABLED_PROPERTY,
        };
    }

    @Override
    public Properties getDefaultPropertyValues(String tenantDomain) throws IdentityGovernanceException {

        Properties defaultValues = new Properties();
        defaultValues.put(MoesifCommonConstants.MOESIF_AUTHENTICATION_PUBLISHER_ENABLED_PROPERTY,
                Boolean.FALSE.toString());
        defaultValues.put(MoesifCommonConstants.MOESIF_REGISTRATION_PUBLISHER_ENABLED_PROPERTY,
                Boolean.FALSE.toString());
        defaultValues.put(MoesifCommonConstants.MOESIF_FLOW_PUBLISHER_ENABLED_PROPERTY,
                Boolean.FALSE.toString());
        defaultValues.put(MoesifCommonConstants.MOESIF_OAUTH_TOKEN_PUBLISHER_ENABLED_PROPERTY,
                Boolean.FALSE.toString());
        return defaultValues;
    }

    @Override
    public Map<String, String> getDefaultPropertyValues(String[] propertyNames, String tenantDomain)
            throws IdentityGovernanceException {

        Map<String, String> defaultValues = new HashMap<>();
        defaultValues.put(MoesifCommonConstants.MOESIF_AUTHENTICATION_PUBLISHER_ENABLED_PROPERTY,
                Boolean.FALSE.toString());
        defaultValues.put(MoesifCommonConstants.MOESIF_REGISTRATION_PUBLISHER_ENABLED_PROPERTY,
                Boolean.FALSE.toString());
        defaultValues.put(MoesifCommonConstants.MOESIF_FLOW_PUBLISHER_ENABLED_PROPERTY,
                Boolean.FALSE.toString());
        defaultValues.put(MoesifCommonConstants.MOESIF_OAUTH_TOKEN_PUBLISHER_ENABLED_PROPERTY,
                Boolean.FALSE.toString());
        return defaultValues;
    }
}
