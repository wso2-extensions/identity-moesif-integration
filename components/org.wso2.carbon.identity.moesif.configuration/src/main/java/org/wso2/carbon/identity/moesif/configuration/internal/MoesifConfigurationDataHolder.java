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

import org.wso2.carbon.identity.governance.IdentityGovernanceService;
import org.wso2.carbon.identity.secret.mgt.core.SecretManager;
import org.wso2.carbon.identity.secret.mgt.core.SecretResolveManager;

/**
 * Data holder for the Moesif configuration management service component.
 */
public class MoesifConfigurationDataHolder {

    private static final MoesifConfigurationDataHolder instance = new MoesifConfigurationDataHolder();

    private SecretManager secretManager;
    private SecretResolveManager secretResolveManager;
    private IdentityGovernanceService identityGovernanceService;

    private MoesifConfigurationDataHolder() {

    }

    public static MoesifConfigurationDataHolder getInstance() {

        return instance;
    }

    public SecretManager getSecretManager() {

        return secretManager;
    }

    public void setSecretManager(SecretManager secretManager) {

        this.secretManager = secretManager;
    }

    public SecretResolveManager getSecretResolveManager() {

        return secretResolveManager;
    }

    public void setSecretResolveManager(SecretResolveManager secretResolveManager) {

        this.secretResolveManager = secretResolveManager;
    }

    public IdentityGovernanceService getIdentityGovernanceService() {

        return identityGovernanceService;
    }

    public void setIdentityGovernanceService(IdentityGovernanceService identityGovernanceService) {

        this.identityGovernanceService = identityGovernanceService;
    }
}
