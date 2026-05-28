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

package org.wso2.carbon.identity.moesif.publisher.http;


import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.event.output.adapter.core.OutputEventAdapterConfiguration;
import org.wso2.carbon.event.output.adapter.http.HTTPEventAdapter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants.NOT_AVAILABLE;

/**
 * Moesif-specific HTTP event adapter that re-shapes the published event JSON to match the Moesif events API contract.
 *
 * <p>The base {@link HTTPEventAdapter} serialises the full databridge event object as-is.  This subclass overrides
 * {@link #buildBody(Object)} to:
 * <ol>
 *   <li>Move fields from the {@code metaData} array to the root of the JSON payload (so Moesif can see them as
 *       first-class event properties such as {@code company_id}, {@code action_name}, {@code user_id}, and
 *       {@code user_agent}).</li>
 *   <li>Nest the original {@code payloadData} array under a {@code "metadata"} key in the root object.</li>
 *   <li>Add a {@code request.time} field from the current instant.</li>
 * </ol>
 * It also overrides {@link #buildHeaders(Object, Map)} to add the {@code User-Agent} HTTP header when a user-agent
 * string is present in the event metadata.
 */
public class MoesifHTTPEventAdapter extends HTTPEventAdapter {

    private static final Log LOG = LogFactory.getLog(MoesifHTTPEventAdapter.class);

    private static final String USER_AGENT_HEADER = "User-Agent";

    private static final String META_DATA_FIELD = "metaData";
    private static final String METADATA_FIELD = "metadata";
    private static final String USER_AGENT_FIELD = "userAgent";
    private static final String IP_ADDRESS_FIELD = "ipAddress";
    private static final String EVENT_FIELD = "event";
    private static final String PAYLOAD_DATA_FIELD = "payloadData";
    private static final String REQUEST_FIELD = "request";
    private static final String TIME_FIELD = "time";
    private static final Gson GSON = new Gson();

    public MoesifHTTPEventAdapter(OutputEventAdapterConfiguration eventAdapterConfiguration,
                                   Map<String, String> globalProperties) {

        super(eventAdapterConfiguration, globalProperties);
    }

    @Override
    protected String buildBody(Object message) {

        JsonObject rawMessage = parseAsJsonObject(message);
        JsonObject result = new JsonObject();

        /*
         * Target shape (matches the Moesif Actions API expectations):
         *
         *   {
         *     "actionName":  "...",   ← first-class Moesif field, flattened from event.metaData
         *     "userId":      "...",   ← first-class
         *     "companyId":   "...",   ← first-class
         *     "metadata":    { ... }, ← event.payloadData nested verbatim
         *     "request":     { "time": "...", "ipAddress": "..." }
         *   }
         *
         * Two metaData keys get special handling:
         *   - userAgent: NEVER goes into the body — it's only emitted as the User-Agent HTTP header
         *     (see buildHeaders below). Putting it at root would pollute Moesif's first-class field set.
         *   - ipAddress: nested under request.ipAddress when meaningful. Skipped when missing /
         *     NOT_AVAILABLE / empty so consumers don't see a placeholder where Moesif expects a real IP.
         *
         * All other metaData entries flatten to the root, which is how Moesif sees properties like
         * `actionName`, `userId`, `companyId`.
         */
        JsonObject request = new JsonObject();
        request.addProperty(TIME_FIELD, Instant.now().toString());

        if (rawMessage != null && rawMessage.has(EVENT_FIELD)) {
            JsonObject event = rawMessage.getAsJsonObject(EVENT_FIELD);
            if (event != null) {
                if (event.has(META_DATA_FIELD)) {
                    JsonObject metadata = event.getAsJsonObject(META_DATA_FIELD);
                    if (metadata != null) {
                        for (Map.Entry<String, JsonElement> entry : metadata.entrySet()) {
                            String key = entry.getKey();
                            JsonElement value = entry.getValue();
                            if (USER_AGENT_FIELD.equals(key)) {
                                // Stays on the HTTP header only; intentionally not in the body.
                                continue;
                            }
                            if (IP_ADDRESS_FIELD.equals(key)) {
                                if (isValid(value)) {
                                    request.add(IP_ADDRESS_FIELD, value);
                                }
                                continue;
                            }
                            result.add(key, value);
                        }
                    }
                }
                if (event.has(PAYLOAD_DATA_FIELD)) {
                    result.add(METADATA_FIELD, event.getAsJsonObject(PAYLOAD_DATA_FIELD));
                }
            }
        }

        result.add(REQUEST_FIELD, request);
        return GSON.toJson(result);
    }

    /**
     * Returns {@code true} when the given JSON element carries a non-blank, non-sentinel string value.
     * Used to decide whether to surface optional fields (currently only {@code ipAddress}) into the
     * output payload — we'd rather omit the field than ship a {@code "NOT_AVAILABLE"} placeholder that
     * the downstream consumer would have to filter back out.
     */
    private static boolean isValid(JsonElement value) {

        if (value == null || value.isJsonNull()) {
            return false;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return true;
        }
        String s = value.getAsString();
        return StringUtils.isNotBlank(s) && !NOT_AVAILABLE.equals(s);
    }

    @Override
    protected Map<String, String> buildHeaders(Object message, Map<String, String> dynamicProperties) {

        Map<String, String> headers = super.buildHeaders(message, dynamicProperties);
        if (headers == null) {
            headers = new HashMap<>();
        }

        Optional<String> userAgent = extractUserAgentFromMessage(message);
        if (userAgent.isPresent()) {
            headers.put(USER_AGENT_HEADER, userAgent.get());
        }
        return headers;
    }

    private Optional<String> extractUserAgentFromMessage(Object message) {

        JsonObject rawMessage = parseAsJsonObject(message);

        if (rawMessage == null) {
            return Optional.empty();
        }

        // Nest the original event payload under "myData".
        if (rawMessage.has(EVENT_FIELD)) {
            JsonObject event = rawMessage.getAsJsonObject(EVENT_FIELD);
            if (event != null && event.has(META_DATA_FIELD)) {
                JsonObject metadata = event.getAsJsonObject(META_DATA_FIELD);
                if (metadata != null && metadata.has(USER_AGENT_FIELD)) {
                    String userAgent = metadata.get(USER_AGENT_FIELD).getAsString();
                    return (StringUtils.isNotBlank(userAgent) || NOT_AVAILABLE.equals(userAgent)) ?
                            Optional.of(userAgent) : Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private JsonObject parseAsJsonObject(Object message) {

        if (message == null) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(message.toString());
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to parse message payload as JSON object.", e);
            }
            return null;
        }
    }
}
