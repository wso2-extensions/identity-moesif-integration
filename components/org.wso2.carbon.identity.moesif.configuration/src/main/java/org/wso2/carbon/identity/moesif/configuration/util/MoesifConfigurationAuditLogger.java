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

package org.wso2.carbon.identity.moesif.configuration.util;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.central.log.mgt.utils.LoggerUtils;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.utils.AuditLog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.wso2.carbon.identity.central.log.mgt.utils.LoggerUtils.triggerAuditLogEvent;

/**
 * V2 audit logger for Moesif publisher configuration changes.
 *
 * <p>Publishes an audit log entry whenever the Moesif publisher configuration of a tenant is
 * added, updated or deleted. The collector API key value is never logged; only whether it was
 * changed by the operation.</p>
 */
public class MoesifConfigurationAuditLogger {

    private static final Log LOG = LogFactory.getLog(MoesifConfigurationAuditLogger.class);
    private static final String TARGET_MOESIF_PUBLISHER = "MoesifPublisherConfiguration";

    private MoesifConfigurationAuditLogger() {

    }

    /**
     * Print an audit log for a Moesif publisher configuration change.
     *
     * @param operation            Operation associated with the state change.
     * @param publisherName        Canonical name of the Moesif publisher configuration.
     * @param publisherTypes       Per-event-type enablement map sent in the request; may be null.
     * @param enableAllPublishers  Whether the master "enable all publishers" toggle was set.
     * @param apiKeyUpdated        Whether the collector API key was set or changed by the operation.
     */
    public static void printAuditLog(Operation operation, String publisherName,
                                     Map<String, Boolean> publisherTypes, boolean enableAllPublishers,
                                     boolean apiKeyUpdated) {

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(LogConstants.PUBLISHER_NAME_FIELD, publisherName);
        data.put(LogConstants.ENABLE_ALL_PUBLISHERS_FIELD, enableAllPublishers);
        data.put(LogConstants.PUBLISHER_TYPES_FIELD,
                publisherTypes != null ? publisherTypes : Collections.emptyMap());
        data.put(LogConstants.API_KEY_UPDATED_FIELD, apiKeyUpdated);
        buildAuditLog(operation, data);
    }

    /**
     * Print an audit log for a Moesif publisher configuration change that carries no request data
     * (e.g. delete).
     *
     * @param operation     Operation associated with the state change.
     * @param publisherName Canonical name of the Moesif publisher configuration.
     */
    public static void printAuditLog(Operation operation, String publisherName) {

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(LogConstants.PUBLISHER_NAME_FIELD, publisherName);
        buildAuditLog(operation, data);
    }

    private static void buildAuditLog(Operation operation, Map<String, Object> data) {

        // Auditing is best-effort: a failure to publish the audit log must never fail the
        // configuration operation itself.
        try {
            String initiatorId = getInitiatorId();
            AuditLog.AuditLogBuilder auditLogBuilder = new AuditLog.AuditLogBuilder(initiatorId,
                    LoggerUtils.getInitiatorType(initiatorId),
                    PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain(),
                    TARGET_MOESIF_PUBLISHER,
                    operation.getLogAction())
                    .data(data);
            triggerAuditLogEvent(auditLogBuilder);
        } catch (RuntimeException e) {
            LOG.warn(String.format("Failed to publish audit log for %s.", operation.getLogAction()), e);
        }
    }

    /**
     * Get the initiator for audit logs: the user ID of the logged-in user when resolvable,
     * {@code System} for internal invocations, or the masked username as a last resort.
     *
     * @return Initiator ID.
     */
    private static String getInitiatorId() {

        String username = PrivilegedCarbonContext.getThreadLocalCarbonContext().getUsername();
        String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        if (StringUtils.isBlank(username)) {
            return LoggerUtils.Initiator.System.name();
        }
        String initiator = null;
        if (StringUtils.isNotBlank(tenantDomain)) {
            initiator = IdentityUtil.getInitiatorId(username, tenantDomain);
        }
        if (StringUtils.isBlank(initiator)) {
            initiator = LoggerUtils.getMaskedContent(username);
        }
        return initiator;
    }

    /**
     * Operations to be logged.
     */
    public enum Operation {

        ADD("add-moesif-publisher"),
        UPDATE("update-moesif-publisher"),
        DELETE("delete-moesif-publisher");

        private final String logAction;

        Operation(String logAction) {

            this.logAction = logAction;
        }

        public String getLogAction() {

            return this.logAction;
        }
    }

    /**
     * Moesif configuration related audit log constants.
     */
    private static class LogConstants {

        public static final String PUBLISHER_NAME_FIELD = "PublisherName";
        public static final String ENABLE_ALL_PUBLISHERS_FIELD = "EnableAllPublishers";
        public static final String PUBLISHER_TYPES_FIELD = "PublisherTypes";
        public static final String API_KEY_UPDATED_FIELD = "ApiKeyUpdated";
    }
}
