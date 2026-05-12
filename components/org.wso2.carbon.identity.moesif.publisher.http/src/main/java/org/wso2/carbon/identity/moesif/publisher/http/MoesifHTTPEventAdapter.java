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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.event.output.adapter.core.OutputEventAdapterConfiguration;
import org.wso2.carbon.event.output.adapter.http.HTTPEventAdapter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.codehaus.stax2.XMLStreamLocation2.NOT_AVAILABLE;
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

        JsonObject rawMessage = GSON.fromJson(message.toString(), JsonObject.class);
        JsonObject result = new JsonObject();

        if (rawMessage == null) {
            return GSON.toJson(result);
        }

        /*
         * The payload data in the message needs to be added as metadata in the moesif request body,
         * and all the entries in the metaData array need to be flattened to the root level of the moesif
         * request body, so that they can be used as first-class properties in Moesif (e.g. company_id, action_name,
         * user_id, user_agent etc.).
         */
        if (rawMessage.has(EVENT_FIELD)) {
            JsonObject event = rawMessage.getAsJsonObject(EVENT_FIELD);
            if (event != null) {
                // Flatten every metadata entry to the root level of the Moesif payload.
                if (event.has(META_DATA_FIELD)) {
                    JsonObject metadata = event.getAsJsonObject(META_DATA_FIELD);
                    if (metadata != null) {
                        for (Map.Entry<String, JsonElement> entry : metadata.entrySet()) {
                            result.add(entry.getKey(), entry.getValue());
                        }
                    }
                }
                if (event.has(PAYLOAD_DATA_FIELD)) {
                    result.add(METADATA_FIELD, event.getAsJsonObject(PAYLOAD_DATA_FIELD));
                }
            }
        }

        // Add request.
        JsonObject request = new JsonObject();
        request.addProperty(TIME_FIELD, Instant.now().toString());
        result.add(REQUEST_FIELD, request);

        return GSON.toJson(result);
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

        JsonObject rawMessage = GSON.fromJson(message.toString(), JsonObject.class);

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
}
