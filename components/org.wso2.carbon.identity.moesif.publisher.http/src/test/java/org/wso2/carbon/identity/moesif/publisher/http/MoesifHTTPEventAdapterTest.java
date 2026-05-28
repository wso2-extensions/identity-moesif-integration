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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.event.output.adapter.core.OutputEventAdapterConfiguration;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link MoesifHTTPEventAdapter}.
 *
 * <p>The {@code buildBody} and {@code buildHeaders} methods are {@code protected}, so an inner
 * {@link TestableAdapter} subclass is used to expose them without reflection.
 */
public class MoesifHTTPEventAdapterTest {

    private TestableAdapter adapter;

    @BeforeClass
    public void setUp() {

        adapter = new TestableAdapter();
    }

    // ── buildBody ────────────────────────────────────────────────────────────────

    /**
     * Happy-path: a full event message with both metaData and payloadData.
     * Expected:
     * <ul>
     *   <li>Each metaData field is promoted to the root of the result JSON.</li>
     *   <li>payloadData is nested under the "metadata" key.</li>
     *   <li>A "request.time" field is present.</li>
     * </ul>
     */
    @Test
    public void testBuildBodyFlattensMetaDataAndNestsPayloadData() {

        String input = buildEventJson(
                /* metaData */ "{\"company_id\":\"org-123\",\"action_name\":\"USER_LOGIN\","
                        + "\"user_id\":\"john@example.com\",\"userAgent\":\"Mozilla/5.0\"}",
                /* payloadData */ "{\"sessionId\":\"s-001\",\"success\":true}");

        String output = adapter.callBuildBody(input);
        JsonObject result = JsonParser.parseString(output).getAsJsonObject();

        // metaData fields promoted to root
        assertEquals(result.get("company_id").getAsString(), "org-123");
        assertEquals(result.get("action_name").getAsString(), "USER_LOGIN");
        assertEquals(result.get("user_id").getAsString(), "john@example.com");

        // payloadData nested under "metadata"
        assertTrue(result.has("metadata"));
        JsonObject metadata = result.getAsJsonObject("metadata");
        assertEquals(metadata.get("sessionId").getAsString(), "s-001");
        assertTrue(metadata.get("success").getAsBoolean());

        // request.time present
        assertTrue(result.has("request"));
        assertTrue(result.getAsJsonObject("request").has("time"));
    }

    /**
     * When the message JSON has no "event" field the result should only contain
     * the "request" object with a "time" field and nothing else.
     */
    @Test
    public void testBuildBodyNoEventFieldProducesOnlyRequestTime() {

        String input = "{\"someOtherField\":\"value\"}";
        String output = adapter.callBuildBody(input);
        JsonObject result = JsonParser.parseString(output).getAsJsonObject();

        assertTrue(result.has("request"), "result must contain 'request'");
        assertTrue(result.getAsJsonObject("request").has("time"), "request must contain 'time'");
        // No extra keys
        assertFalse(result.has("metadata"));
        assertFalse(result.has("company_id"));
    }

    /**
     * When the event has payloadData but no metaData, the payloadData is still nested under
     * "metadata" and request.time is still added; no extra root-level fields from metaData.
     */
    @Test
    public void testBuildBodyEventWithoutMetaData() {

        String input = "{ \"event\": { \"payloadData\": {\"field1\":\"v1\"} } }";
        String output = adapter.callBuildBody(input);
        JsonObject result = JsonParser.parseString(output).getAsJsonObject();

        assertTrue(result.has("metadata"));
        assertEquals(result.getAsJsonObject("metadata").get("field1").getAsString(), "v1");
        assertTrue(result.has("request"));
        // No metaData fields at root (no company_id / action_name / user_id / userAgent)
        assertFalse(result.has("company_id"));
    }

    /**
     * When the event has metaData but no payloadData, the metaData fields are promoted to root
     * and there is no "metadata" key in the result.
     */
    @Test
    public void testBuildBodyEventWithoutPayloadData() {

        String input = "{ \"event\": { \"metaData\": {\"company_id\":\"org-456\",\"action_name\":\"LOGOUT\"} } }";
        String output = adapter.callBuildBody(input);
        JsonObject result = JsonParser.parseString(output).getAsJsonObject();

        assertEquals(result.get("company_id").getAsString(), "org-456");
        assertEquals(result.get("action_name").getAsString(), "LOGOUT");
        assertFalse(result.has("metadata"));
        assertTrue(result.has("request"));
    }

    /**
     * The "request.time" value must be a valid ISO-8601 UTC instant.
     */
    @Test
    public void testBuildBodyRequestTimeIsValidIso8601Instant() {

        String input = buildEventJson("{}", "{}");
        String output = adapter.callBuildBody(input);
        JsonObject result = JsonParser.parseString(output).getAsJsonObject();

        String timeValue = result.getAsJsonObject("request").get("time").getAsString();
        // Should not throw DateTimeParseException
        Instant parsed = Instant.parse(timeValue);
        assertNotNull(parsed);
    }

    /**
     * Passing the JSON string "null" causes GSON to return null for the parsed JsonObject;
     * the method must return an empty JSON object rather than throwing.
     */
    @Test
    public void testBuildBodyNullJsonReturnsEmptyObject() {

        // GSON.fromJson("null", JsonObject.class) returns null in Gson 2.x
        String output = adapter.callBuildBody("null");
        assertNotNull(output);
        // Must be valid JSON
        JsonObject result = JsonParser.parseString(output).getAsJsonObject();
        assertNotNull(result);
    }

    // ── buildHeaders ─────────────────────────────────────────────────────────────

    /**
     * When the event metaData contains a non-blank userAgent string, the "User-Agent" HTTP header
     * must be added to the headers map.
     */
    @Test
    public void testBuildHeadersAddsUserAgentWhenPresent() {

        String input = buildEventJson(
                "{\"userAgent\":\"Mozilla/5.0 (Macintosh)\",\"company_id\":\"org-1\"}",
                "{}");

        Map<String, String> headers = adapter.callBuildHeaders(input, new HashMap<>());

        assertNotNull(headers);
        assertTrue(headers.containsKey("User-Agent"),
                "User-Agent header must be present when userAgent is in metaData");
        assertEquals(headers.get("User-Agent"), "Mozilla/5.0 (Macintosh)");
    }

    /**
     * When the event metaData has userAgent = "NOT_AVAILABLE" the header must still be added
     * (the adapter preserves NOT_AVAILABLE so downstream consumers can identify missing data).
     */
    @Test
    public void testBuildHeadersAddsNotAvailableUserAgent() {

        String input = buildEventJson(
                "{\"userAgent\":\"NOT_AVAILABLE\",\"company_id\":\"org-2\"}",
                "{}");

        Map<String, String> headers = adapter.callBuildHeaders(input, new HashMap<>());

        assertNotNull(headers);
        assertTrue(headers.containsKey("User-Agent"),
                "User-Agent header must be present even when value is NOT_AVAILABLE");
        assertEquals(headers.get("User-Agent"), "NOT_AVAILABLE");
    }

    /**
     * When the event contains no metaData.userAgent field, the "User-Agent" header must
     * not be injected.
     */
    @Test
    public void testBuildHeadersNoUserAgentWhenFieldAbsent() {

        String input = buildEventJson("{\"company_id\":\"org-3\"}", "{}");

        Map<String, String> headers = adapter.callBuildHeaders(input, new HashMap<>());

        // Headers map may be null (super returned null and we got default empty map); either way
        // User-Agent must not be present.
        if (headers != null) {
            assertFalse(headers.containsKey("User-Agent"),
                    "User-Agent header must not be added when userAgent field is absent");
        }
    }

    /**
     * When the event metaData.userAgent is a blank string ("") the header must not be injected
     * because the value carries no useful information.
     */
    @Test
    public void testBuildHeadersBlankUserAgentNotAdded() {

        String input = buildEventJson("{\"userAgent\":\"\",\"company_id\":\"org-4\"}", "{}");

        Map<String, String> headers = adapter.callBuildHeaders(input, new HashMap<>());

        if (headers != null) {
            assertFalse(headers.containsKey("User-Agent"),
                    "User-Agent header must not be added for a blank userAgent value");
        }
    }

    /**
     * When the message has no "event" field at all, no User-Agent header should be injected.
     */
    @Test
    public void testBuildHeadersNoEventFieldProducesNoUserAgent() {

        String input = "{\"someField\":\"value\"}";

        Map<String, String> headers = adapter.callBuildHeaders(input, new HashMap<>());

        if (headers != null) {
            assertFalse(headers.containsKey("User-Agent"),
                    "User-Agent must not be injected when no event field is present in the message");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    /**
     * Build a minimal event wrapper JSON string with the supplied metaData and payloadData JSON bodies.
     *
     * @param metaDataJson    JSON object literal for the metaData block, e.g. {@code "{\"key\":\"val\"}"}
     * @param payloadDataJson JSON object literal for the payloadData block
     * @return full event JSON string
     */
    private String buildEventJson(String metaDataJson, String payloadDataJson) {

        return "{ \"event\": { \"metaData\": " + metaDataJson
                + ", \"payloadData\": " + payloadDataJson + " } }";
    }

    // ── inner test-helper ─────────────────────────────────────────────────────────

    /**
     * Thin subclass that exposes the {@code protected} methods of {@link MoesifHTTPEventAdapter}
     * so they can be called from tests without reflection.
     */
    private static class TestableAdapter extends MoesifHTTPEventAdapter {

        TestableAdapter() {

            super(buildAdapterConfig(), new HashMap<>());
        }

        private static OutputEventAdapterConfiguration buildAdapterConfig() {

            OutputEventAdapterConfiguration config = new OutputEventAdapterConfiguration();
            config.setStaticProperties(new HashMap<>());
            return config;
        }

        String callBuildBody(Object message) {

            return buildBody(message);
        }

        Map<String, String> callBuildHeaders(Object message, Map<String, String> dynamicProperties) {

            return buildHeaders(message, dynamicProperties);
        }
    }
}
