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
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link MoesifHTTPEventAdapter}.
 *
 * <p>The {@code parseEvent}, {@code buildBody}, {@code buildHeaders} and {@code resolveUrl} methods are
 * {@code protected}, so an inner {@link TestableAdapter} subclass is used to expose them without reflection.
 */
public class MoesifHTTPEventAdapterTest {

    private static final String BASE_URL = "https://api.moesif.net/v1";

    private TestableAdapter adapter;

    @BeforeClass
    public void setUp() {

        adapter = new TestableAdapter();
    }

    // ── parseEvent ───────────────────────────────────────────────────────────────

    /**
     * The published message is deserialised once and the {@code event} member is returned.
     */
    @Test
    public void testParseEventReturnsEventObject() {

        JsonObject event = adapter.callParseEvent(buildEventJson("{\"company_id\":\"org-1\"}", "{}"));

        assertNotNull(event);
        assertTrue(event.has("metaData"));
        assertTrue(event.has("payloadData"));
    }

    /**
     * Messages without an "event" member, non-object messages and the JSON literal "null"
     * must all yield {@code null} instead of throwing.
     */
    @Test
    public void testParseEventToleratesMalformedMessages() {

        assertNull(adapter.callParseEvent("{\"someOtherField\":\"value\"}"));
        assertNull(adapter.callParseEvent("not json at all"));
        assertNull(adapter.callParseEvent("null"));
        assertNull(adapter.callParseEvent(null));
        assertNull(adapter.callParseEvent("{\"event\":\"not-an-object\"}"));
    }

    // ── buildBody ────────────────────────────────────────────────────────────────

    /**
     * Happy-path: a full event with both metaData and payloadData.
     * Expected:
     * <ul>
     *   <li>Each metaData field is promoted to the root of the result JSON.</li>
     *   <li>payloadData is nested under the "metadata" key.</li>
     *   <li>A "request.time" field is present.</li>
     * </ul>
     */
    @Test
    public void testBuildBodyFlattensMetaDataAndNestsPayloadData() {

        JsonObject event = adapter.callParseEvent(buildEventJson(
                /* metaData */ "{\"company_id\":\"org-123\",\"action_name\":\"USER_LOGIN\","
                        + "\"user_id\":\"john@example.com\",\"userAgent\":\"Mozilla/5.0\"}",
                /* payloadData */ "{\"sessionId\":\"s-001\",\"success\":true}"));

        JsonObject result = JsonParser.parseString(adapter.callBuildBody(event)).getAsJsonObject();

        // metaData fields promoted to root
        assertEquals(result.get("company_id").getAsString(), "org-123");
        assertEquals(result.get("action_name").getAsString(), "USER_LOGIN");
        assertEquals(result.get("user_id").getAsString(), "john@example.com");

        // userAgent stays on the HTTP header only
        assertFalse(result.has("userAgent"));

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
     * A null event (message without an "event" member) produces only the "request" object
     * with a "time" field and nothing else.
     */
    @Test
    public void testBuildBodyNullEventProducesOnlyRequestTime() {

        JsonObject result = JsonParser.parseString(adapter.callBuildBody(null)).getAsJsonObject();

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

        JsonObject event = adapter.callParseEvent("{ \"event\": { \"payloadData\": {\"field1\":\"v1\"} } }");
        JsonObject result = JsonParser.parseString(adapter.callBuildBody(event)).getAsJsonObject();

        assertTrue(result.has("metadata"));
        assertEquals(result.getAsJsonObject("metadata").get("field1").getAsString(), "v1");
        assertTrue(result.has("request"));
        assertFalse(result.has("company_id"));
    }

    /**
     * When the event has metaData but no payloadData, the metaData fields are promoted to root
     * and there is no "metadata" key in the result.
     */
    @Test
    public void testBuildBodyEventWithoutPayloadData() {

        JsonObject event = adapter.callParseEvent(
                "{ \"event\": { \"metaData\": {\"company_id\":\"org-456\",\"action_name\":\"LOGOUT\"} } }");
        JsonObject result = JsonParser.parseString(adapter.callBuildBody(event)).getAsJsonObject();

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

        JsonObject event = adapter.callParseEvent(buildEventJson("{}", "{}"));
        JsonObject result = JsonParser.parseString(adapter.callBuildBody(event)).getAsJsonObject();

        String timeValue = result.getAsJsonObject("request").get("time").getAsString();
        // Should not throw DateTimeParseException
        Instant parsed = Instant.parse(timeValue);
        assertNotNull(parsed);
    }

    /**
     * A meaningful ipAddress is nested under request.ipAddress, never at the root;
     * a NOT_AVAILABLE ipAddress is omitted entirely.
     */
    @Test
    public void testBuildBodyIpAddressHandling() {

        JsonObject event = adapter.callParseEvent(
                buildEventJson("{\"ipAddress\":\"203.0.113.7\"}", "{}"));
        JsonObject result = JsonParser.parseString(adapter.callBuildBody(event)).getAsJsonObject();
        assertFalse(result.has("ipAddress"));
        assertEquals(result.getAsJsonObject("request").get("ipAddress").getAsString(), "203.0.113.7");

        event = adapter.callParseEvent(buildEventJson("{\"ipAddress\":\"NOT_AVAILABLE\"}", "{}"));
        result = JsonParser.parseString(adapter.callBuildBody(event)).getAsJsonObject();
        assertFalse(result.has("ipAddress"));
        assertFalse(result.getAsJsonObject("request").has("ipAddress"));
    }

    /**
     * Identity fields (userId / anonymous_id / anonymousId) are omitted from the body when blank or
     * NOT_AVAILABLE — Moesif must never receive a placeholder identity — but pass through when they
     * carry a meaningful value.
     */
    @Test
    public void testBuildBodyOmitsBlankOrNotAvailableIdentityFields() {

        // Placeholder values are dropped.
        JsonObject event = adapter.callParseEvent(buildEventJson(
                "{\"userId\":\"NOT_AVAILABLE\",\"anonymous_id\":\"\",\"anonymousId\":\"NOT_AVAILABLE\","
                        + "\"company_id\":\"org-7\"}", "{}"));
        JsonObject result = JsonParser.parseString(adapter.callBuildBody(event)).getAsJsonObject();

        assertFalse(result.has("userId"), "NOT_AVAILABLE userId must be omitted");
        assertFalse(result.has("anonymous_id"), "blank anonymous_id must be omitted");
        assertFalse(result.has("anonymousId"), "NOT_AVAILABLE anonymousId must be omitted");
        assertEquals(result.get("company_id").getAsString(), "org-7");

        // Meaningful values pass through to the root.
        event = adapter.callParseEvent(buildEventJson(
                "{\"userId\":\"user-1\",\"anonymous_id\":\"ctx_abc\",\"anonymousId\":\"ctx_def\"}", "{}"));
        result = JsonParser.parseString(adapter.callBuildBody(event)).getAsJsonObject();

        assertEquals(result.get("userId").getAsString(), "user-1");
        assertEquals(result.get("anonymous_id").getAsString(), "ctx_abc");
        assertEquals(result.get("anonymousId").getAsString(), "ctx_def");
    }

    /**
     * The urlSuffix metaData field is routing metadata only and must never appear in the body.
     */
    @Test
    public void testBuildBodyExcludesUrlSuffix() {

        JsonObject event = adapter.callParseEvent(
                buildEventJson("{\"urlSuffix\":\"actions\",\"company_id\":\"org-9\"}", "{}"));
        JsonObject result = JsonParser.parseString(adapter.callBuildBody(event)).getAsJsonObject();

        assertFalse(result.has("urlSuffix"), "urlSuffix must not be published in the body");
        assertEquals(result.get("company_id").getAsString(), "org-9");
    }

    // ── buildHeaders ─────────────────────────────────────────────────────────────

    /**
     * When the event metaData contains a non-blank userAgent string, the "User-Agent" HTTP header
     * must be added to the headers map.
     */
    @Test
    public void testBuildHeadersAddsUserAgentWhenPresent() {

        Map<String, String> headers = adapter.callBuildHeaders(
                buildEventJson("{\"userAgent\":\"Mozilla/5.0 (Macintosh)\",\"company_id\":\"org-1\"}", "{}"),
                new HashMap<>());

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

        Map<String, String> headers = adapter.callBuildHeaders(
                buildEventJson("{\"userAgent\":\"NOT_AVAILABLE\",\"company_id\":\"org-2\"}", "{}"),
                new HashMap<>());

        assertNotNull(headers);
        assertTrue(headers.containsKey("User-Agent"),
                "User-Agent header must be present even when value is NOT_AVAILABLE");
        assertEquals(headers.get("User-Agent"), "NOT_AVAILABLE");
    }

    /**
     * When the event contains no metaData.userAgent field, or a blank one, the "User-Agent"
     * header must not be injected.
     */
    @Test
    public void testBuildHeadersNoUserAgentWhenAbsentOrBlank() {

        Map<String, String> headers = adapter.callBuildHeaders(
                buildEventJson("{\"company_id\":\"org-3\"}", "{}"), new HashMap<>());
        assertFalse(headers.containsKey("User-Agent"),
                "User-Agent header must not be added when userAgent field is absent");

        headers = adapter.callBuildHeaders(
                buildEventJson("{\"userAgent\":\"\",\"company_id\":\"org-4\"}", "{}"), new HashMap<>());
        assertFalse(headers.containsKey("User-Agent"),
                "User-Agent header must not be added for a blank userAgent value");
    }

    /**
     * Statically configured headers from the publisher configuration are parsed and merged.
     */
    @Test
    public void testBuildHeadersParsesConfiguredHeaders() {

        Map<String, String> dynamicProperties = new HashMap<>();
        dynamicProperties.put("http.headers", "X-Custom:abc,X-Other:def");

        Map<String, String> headers = adapter.callBuildHeaders(
                buildEventJson("{\"userAgent\":\"UA\"}", "{}"), dynamicProperties);

        assertEquals(headers.get("X-Custom"), "abc");
        assertEquals(headers.get("X-Other"), "def");
        assertEquals(headers.get("User-Agent"), "UA");
    }

    // ── resolveUrl ───────────────────────────────────────────────────────────────

    /**
     * A urlSuffix in the event metaData is appended to the base URL so the same publisher can
     * target the Moesif Actions / Users / Companies APIs per event.
     */
    @Test
    public void testResolveUrlAppendsSuffix() {

        assertEquals(adapter.callResolveUrl(BASE_URL, "{\"urlSuffix\":\"actions\"}"),
                BASE_URL + "/actions");
        assertEquals(adapter.callResolveUrl(BASE_URL, "{\"urlSuffix\":\"users\"}"),
                BASE_URL + "/users");
        assertEquals(adapter.callResolveUrl(BASE_URL, "{\"urlSuffix\":\"companies\"}"),
                BASE_URL + "/companies");
    }

    /**
     * Redundant slashes on either side of the join must not produce double slashes.
     */
    @Test
    public void testResolveUrlNormalisesSlashes() {

        assertEquals(adapter.callResolveUrl(BASE_URL + "/", "{\"urlSuffix\":\"actions\"}"),
                BASE_URL + "/actions");
        assertEquals(adapter.callResolveUrl(BASE_URL, "{\"urlSuffix\":\"/actions\"}"),
                BASE_URL + "/actions");
        assertEquals(adapter.callResolveUrl(BASE_URL + "/", "{\"urlSuffix\":\"/actions/\"}"),
                BASE_URL + "/actions");
    }

    /**
     * Multi-segment suffixes are allowed (e.g. versioned sub-resources).
     */
    @Test
    public void testResolveUrlAllowsMultiSegmentSuffix() {

        assertEquals(adapter.callResolveUrl(BASE_URL, "{\"urlSuffix\":\"actions/batch\"}"),
                BASE_URL + "/actions/batch");
    }

    /**
     * Missing, blank and NOT_AVAILABLE suffixes leave the base URL untouched.
     */
    @Test
    public void testResolveUrlWithoutUsableSuffixReturnsBaseUrl() {

        assertEquals(adapter.callResolveUrl(BASE_URL, "{}"), BASE_URL);
        assertEquals(adapter.callResolveUrl(BASE_URL, "{\"urlSuffix\":\"\"}"), BASE_URL);
        assertEquals(adapter.callResolveUrl(BASE_URL, "{\"urlSuffix\":\"NOT_AVAILABLE\"}"), BASE_URL);
        assertEquals(adapter.callResolveUrl(BASE_URL, null), BASE_URL);
    }

    /**
     * Suffix values that could rewrite the target authority, path or query (path traversal,
     * query / fragment separators, spaces) are rejected and the base URL is used instead.
     */
    @Test
    public void testResolveUrlRejectsUnsafeSuffixes() {

        assertEquals(adapter.callResolveUrl(BASE_URL, "{\"urlSuffix\":\"../admin\"}"), BASE_URL);
        assertEquals(adapter.callResolveUrl(BASE_URL, "{\"urlSuffix\":\"actions?x=1\"}"), BASE_URL);
        assertEquals(adapter.callResolveUrl(BASE_URL, "{\"urlSuffix\":\"actions#frag\"}"), BASE_URL);
        assertEquals(adapter.callResolveUrl(BASE_URL, "{\"urlSuffix\":\"act ions\"}"), BASE_URL);
        // '.' is not path-safe per the allowed pattern, so a host-like suffix is rejected too.
        assertEquals(adapter.callResolveUrl(BASE_URL, "{\"urlSuffix\":\"//evil.example\"}"), BASE_URL);
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

        JsonObject callParseEvent(Object message) {

            return parseEvent(message);
        }

        String callBuildBody(JsonObject event) {

            return buildBody(event);
        }

        Map<String, String> callBuildHeaders(String messageJson, Map<String, String> dynamicProperties) {

            JsonObject event = parseEvent(messageJson);
            JsonObject metaData = event != null && event.has("metaData") && event.get("metaData").isJsonObject()
                    ? event.getAsJsonObject("metaData") : null;
            return buildHeaders(metaData, dynamicProperties);
        }

        String callResolveUrl(String baseUrl, String metaDataJson) {

            JsonObject metaData = metaDataJson != null
                    ? JsonParser.parseString(metaDataJson).getAsJsonObject() : null;
            return resolveUrl(baseUrl, metaData);
        }
    }
}
