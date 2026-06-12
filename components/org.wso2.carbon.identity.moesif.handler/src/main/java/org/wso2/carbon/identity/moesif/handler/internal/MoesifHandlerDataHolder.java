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

import org.wso2.carbon.consent.mgt.core.ConsentManager;
import org.wso2.carbon.event.stream.core.EventStreamService;
import org.wso2.carbon.identity.governance.IdentityGovernanceService;
import org.wso2.carbon.identity.organization.management.service.OrganizationManager;

/**
 * Data holder for the Moesif event handler service component.
 */
public class MoesifHandlerDataHolder {

    private static final MoesifHandlerDataHolder instance = new MoesifHandlerDataHolder();

    private EventStreamService publisherService;
    private OrganizationManager organizationManager;
    private IdentityGovernanceService identityGovernanceService;
    private ConsentManager consentManager;

    private MoesifHandlerDataHolder() {

    }

    public static MoesifHandlerDataHolder getInstance() {

        return instance;
    }

    public EventStreamService getPublisherService() {

        return publisherService;
    }

    public void setPublisherService(EventStreamService publisherService) {

        this.publisherService = publisherService;
    }

    public OrganizationManager getOrganizationManager() {

        return organizationManager;
    }

    public void setOrganizationManager(OrganizationManager organizationManager) {

        this.organizationManager = organizationManager;
    }

    public IdentityGovernanceService getIdentityGovernanceService() {

        return identityGovernanceService;
    }

    public void setIdentityGovernanceService(IdentityGovernanceService identityGovernanceService) {

        this.identityGovernanceService = identityGovernanceService;
    }

    public ConsentManager getConsentManager() {

        return consentManager;
    }

    public void setConsentManager(ConsentManager consentManager) {

        this.consentManager = consentManager;
    }
}
