/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.seyren.core.service.notification;

import com.seyren.core.domain.*;
import com.seyren.core.exception.NotificationFailedException;
import com.seyren.core.util.config.SeyrenConfig;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import org.python.antlr.ast.Str;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;


import javax.inject.Inject;
import javax.inject.Named;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
//import com.google.code.gson;

@Named
public class StrideNotificationService implements NotificationService {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(StrideNotificationService.class);

    private final SeyrenConfig seyrenConfig;
    private final String baseUrl;
    private final String cloudId;

    @Inject
    public StrideNotificationService(SeyrenConfig seyrenConfig) {
        this.seyrenConfig = seyrenConfig;
        this.baseUrl = seyrenConfig.getStrideBaseUrl();
        this.cloudId = seyrenConfig.getStrideCloudId();
    }

    protected StrideNotificationService(SeyrenConfig seyrenConfig, String baseUrl, String cloudId) {
        this.seyrenConfig = seyrenConfig;
        this.baseUrl = baseUrl;
        this.cloudId = cloudId;
    }

    private String getJsonValue(String jsonData, String keyName){
        String value = "";
        try {
            JsonFactory factory = new JsonFactory();
            JsonParser parser = factory.createParser(jsonData);
            while (!parser.isClosed()) {
                JsonToken jsonToken = parser.nextToken();

                if (JsonToken.FIELD_NAME.equals(jsonToken)) {
                    String fieldName = parser.getCurrentName();
                    parser.nextToken();
                    if (keyName.equals(fieldName)) {
                        value = parser.getValueAsString();
                    }
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Stride access token generated. Token: ", keyName);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error parsing response", e);
        }
        return value;
    }

    private String getAccessToken() {
        LOGGER.info("Getting Stride access_token");

        String clientId = seyrenConfig.getStrideClientId();
        String clientSecret = seyrenConfig.getStrideClientSecret();
        String url = "https://auth.atlassian.com/oauth/token";

        List<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("client_id", clientId));
        parameters.add(new BasicNameValuePair("client_secret", clientSecret));
        parameters.add(new BasicNameValuePair("grant_type", "client_credentials"));
        parameters.add(new BasicNameValuePair("audience", "api.atlassian.com"));

        HttpClient client = HttpClientBuilder.create().useSystemProperties().build();
        HttpPost post;
        String accessToken = "";
        post = new HttpPost(url);
        post.setHeader("content-type", "application/x-www-form-urlencoded");

        try {
            post.setEntity(new UrlEncodedFormEntity(parameters));
            if (LOGGER.isDebugEnabled()) {
                LOGGER.info("> parameters: {}", parameters);
            }
            HttpResponse response = client.execute(post);
            int responseCode = response.getStatusLine().getStatusCode();
            String responseBody = new BasicResponseHandler().handleResponse(response);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.info("> parameters: {}", parameters);
                LOGGER.debug("Access token response generated.  " +
                        "Status: {}, Body: {}", responseCode, responseBody);
            }
            if (responseCode == 200) {
                accessToken = getJsonValue(responseBody, "access_token");
            } else {
                LOGGER.error("Could not generate Stride access token. Server response code: {}, response body: {}"
                        + responseCode, responseBody);
            }
        } catch (Exception e) {
            LOGGER.warn("Error getting Access Token from Stride API", e);
        } finally {
            post.releaseConnection();
            HttpClientUtils.closeQuietly(client);
        }

        return accessToken;
    }

    private String getConversationList(String cloudId, String accessToken){
        String url = baseUrl + "/site/" + cloudId + "/conversation";
        String conversationList = "";
        LOGGER.info("Getting list of conversations");

        HttpClient client = HttpClientBuilder.create().build();
        HttpGet request = new HttpGet(url);
        request.addHeader("authorization", "Bearer " + accessToken);
        LOGGER.debug("Sending request for conversation list");

        try {
            HttpResponse response = client.execute(request);
            int responseCode = response.getStatusLine().getStatusCode();
            String responseBody = new BasicResponseHandler().handleResponse(response);
            LOGGER.debug("Conversation list request response generated. Status: {}, Body: {}", responseCode, responseBody);
            if (responseCode == 200) {
                conversationList = responseBody;
            } else {
                LOGGER.error("Could not get list of conversations this bot is in. Server response code: {}, response body: {}"
                        + responseCode, responseBody);
            }
            System.out.println("Response Code : " + responseCode);
            System.out.println("Response Body : " + responseBody);
        } catch (Exception e) {
            LOGGER.warn("Error getting account information from Stride API", e);
        }
        return conversationList;
    }



    public void sendNotification(Check check, Subscription subscription, List<Alert> alerts) throws NotificationFailedException {
        String targetChannel = "dev-monitoring";
        String accessToken = getAccessToken();
        String conversationList = getConversationList(cloudId, accessToken);
        System.out.println(conversationList);

//        sendMessage("This is a seyren test", MessageColor.RANDOM, );

//        String[] conversationIds = subscription.getTarget().split(",");
//        try {
//            if (check.getState() == AlertType.ERROR) {
//                String message = getStrideMessage(check);
//                sendMessage(message, MessageColor.RED, from, accessToken, true);
//            } else if (check.getState() == AlertType.WARN) {
//                String message = getStrideMessage(check);
//                sendMessage(message, MessageColor.YELLOW, from, accessToken, true);
//            } else if (check.getState() == AlertType.OK) {
//                String message = getStrideMessage(check);
//                sendMessage(message, MessageColor.GREEN, from, accessToken, true);
//            } else {
//                LOGGER.warn("Did not send notification to HipChat for check in state: {}", check.getState());
//            }
//        } catch (Exception e) {
//            throw new NotificationFailedException("Failed to send notification to HipChat", e);
//        }
    }
    
    private String getStrideMessage(Check check) {
        String message = "Check <a href=" + seyrenConfig.getBaseUrl() + "/#/checks/" + check.getId() + ">" + check.getName() + "</a> has entered its " + check.getState().toString() + " state.";
        return message;
    }
    
    private void sendMessage(String message, MessageColor color, String[] conversationIds, String from, String accessToken, boolean notify) {
        for (String conversationId : conversationIds) {
            LOGGER.info("Posting: {} to {}: {} {}", from, conversationId, message, color);
            HttpClient client = HttpClientBuilder.create().useSystemProperties().build();
            HttpPost post = new HttpPost();
            String url;

            try {
                url = baseUrl + "/v2/room/" + URLEncoder.encode(conversationId, "UTF-8").replaceAll("\\+", "%20") + "/notification?auth_token=" + accessToken;
                post = new HttpPost(url);
                List<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
                parameters.add(new BasicNameValuePair("message", message));
                parameters.add(new BasicNameValuePair("color", color.name().toLowerCase()));
                parameters.add(new BasicNameValuePair("message_format", "html"));
                if (notify) {
                    parameters.add(new BasicNameValuePair("notify", "true"));
                }
                post.setEntity(new UrlEncodedFormEntity(parameters));
                client.execute(post);
            } catch (Exception e) {
                LOGGER.warn("Error posting to HipChat", e);
            } finally {
                post.releaseConnection();
                HttpClientUtils.closeQuietly(client);
            }
        }
    }
    
    @Override
    public boolean canHandle(SubscriptionType subscriptionType) {
        return subscriptionType == SubscriptionType.STRIDE;
    }
    
    private enum MessageColor {
        YELLOW, RED, GREEN, PURPLE, RANDOM;
    }
}
