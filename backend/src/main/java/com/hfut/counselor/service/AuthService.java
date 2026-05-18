package com.hfut.counselor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfut.counselor.dto.AuthDtos;
import com.hfut.counselor.dto.UserDto;
import com.hfut.counselor.entity.User;
import com.hfut.counselor.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.io.StringReader;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserService userService;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    private final Map<String, String> stateStore = new ConcurrentHashMap<String, String>();
    private final WebClient webClient = WebClient.builder().build();

    @Value("${app.frontend-url:/counselor/}")
    private String defaultFrontendUrl;
    @Value("${app.portal.authorization-uri:}")
    private String authorizationUri;
    @Value("${app.portal.cas-base-url:}")
    private String casBaseUrl;
    @Value("${app.portal.login-type:}")
    private String loginType;
    @Value("${app.portal.token-uri:}")
    private String tokenUri;
    @Value("${app.portal.userinfo-uri:}")
    private String userinfoUri;
    @Value("${app.portal.client-id:}")
    private String clientId;
    @Value("${app.portal.client-secret:}")
    private String clientSecret;
    @Value("${app.portal.redirect-uri:}")
    private String redirectUri;
    @Value("${app.portal.scope:openid profile email}")
    private String scope;

    public String buildLoginUrl(String callback) {
        String state = UUID.randomUUID().toString().replace("-", "");
        stateStore.put(state, StringUtils.hasText(callback) ? callback : defaultFrontendUrl);
        if (StringUtils.hasText(casBaseUrl)) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(trimTrailingSlash(casBaseUrl) + "/login")
                    .queryParam("service", buildCasServiceUrl(state));
            if (StringUtils.hasText(loginType)) {
                builder.queryParam("loginType", loginType);
            }
            return builder.build().encode().toUriString();
        }
        if (!StringUtils.hasText(authorizationUri)) {
            return defaultFrontendUrl + "?auth_error=portal_not_configured";
        }
        return UriComponentsBuilder.fromHttpUrl(authorizationUri)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", scope)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    public CallbackResult handleCasCallback(String ticket, String state) {
        String callback = stateStore.remove(state);
        if (!StringUtils.hasText(callback)) {
            return CallbackResult.failure(defaultFrontendUrl, "invalid_state");
        }
        if (!StringUtils.hasText(ticket)) {
            return CallbackResult.failure(callback, "missing_ticket");
        }
        try {
            CasProfile profile = validateCasTicket(ticket, buildCasServiceUrl(state));
            if (!StringUtils.hasText(profile.username)) {
                return CallbackResult.failure(callback, "missing_username");
            }
            User user = userService.upsertPortalUser(
                    profile.username,
                    firstText(profile.attributes, "name", "userName", "displayName"),
                    firstText(profile.attributes, "email"),
                    firstText(profile.attributes, "mobile", "phone", "tel"),
                    firstText(profile.attributes, "avatar", "picture"),
                    firstText(profile.attributes, "userId", "accountId", "id")
            );
            return CallbackResult.success(callback, buildLoginResponse(user));
        } catch (Exception e) {
            return CallbackResult.failure(callback, "cas_ticket_validate_failed");
        }
    }

    public String buildCasLogoutUrl(String callback) {
        String target = StringUtils.hasText(callback) ? callback : defaultFrontendUrl;
        if (!StringUtils.hasText(casBaseUrl)) {
            return buildLogoutRedirectUrl(target);
        }
        String service = UriComponentsBuilder.fromHttpUrl(redirectUri.replace("/callback", "/logout"))
                .queryParam("callback", target)
                .queryParam("logout", "logout")
                .build()
                .encode()
                .toUriString();
        return UriComponentsBuilder.fromHttpUrl(trimTrailingSlash(casBaseUrl) + "/logout")
                .queryParam("service", service)
                .build()
                .encode()
                .toUriString();
    }

    public String buildLogoutRedirectUrl(String callback) {
        String target = StringUtils.hasText(callback) ? callback : defaultFrontendUrl;
        String sep = target.contains("?") ? "&" : "?";
        return target + sep + "auth_logout=1";
    }

    public String buildSloResponse(String callback) {
        String json = "{\"code\":0,\"message\":null,\"data\":{\"success\":\"注销成功\"}}";
        if (StringUtils.hasText(callback)) {
            return callback + "(" + json + ");";
        }
        return json;
    }


    public CallbackResult handleOidcCallback(String code, String state) {
        String callback = stateStore.remove(state);
        if (!StringUtils.hasText(callback)) {
            return CallbackResult.failure(defaultFrontendUrl, "invalid_state");
        }
        try {
            JsonNode token = exchangeToken(code);
            String accessToken = token.path("access_token").asText("");
            if (!StringUtils.hasText(accessToken)) {
                return CallbackResult.failure(callback, "missing_access_token");
            }
            JsonNode profile = loadProfile(accessToken);
            String username = firstText(profile, "preferred_username", "username", "sub");
            if (!StringUtils.hasText(username)) {
                return CallbackResult.failure(callback, "missing_username");
            }
            User user = userService.upsertPortalUser(
                    username,
                    firstText(profile, "name", "display_name", "nickname"),
                    firstText(profile, "email"),
                    firstText(profile, "phone_number", "phone"),
                    firstText(profile, "picture", "avatar"),
                    firstText(profile, "sub")
            );
            return CallbackResult.success(callback, buildLoginResponse(user));
        } catch (Exception e) {
            return CallbackResult.failure(callback, "callback_failed");
        }
    }

    public AuthDtos.LoginResponse devLogin(String username, String displayName) {
        User user = userService.createOrUpdateDevUser(username, displayName);
        return buildLoginResponse(user);
    }

    public String buildRedirectUrl(CallbackResult result) {
        String target = result.callbackUrl;
        String sep = target.contains("?") ? "&" : "?";
        if (!result.success) {
            return target + sep + "auth_error=" + urlEncode(result.error);
        }
        return target + sep + "auth_callback=1&token=" + urlEncode(result.loginResponse.getToken())
                + "&user=" + urlEncode(writeJson(result.loginResponse.getUser()));
    }

    private AuthDtos.LoginResponse buildLoginResponse(User user) {
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return new AuthDtos.LoginResponse(token, UserDto.from(user));
    }

    private JsonNode exchangeToken(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<String, String>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        return webClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(body))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    private JsonNode loadProfile(String accessToken) {
        return webClient.get()
                .uri(userinfoUri)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    private CasProfile validateCasTicket(String ticket, String serviceUrl) throws Exception {
        String xml = webClient.get()
                .uri(UriComponentsBuilder.fromHttpUrl(trimTrailingSlash(casBaseUrl) + "/serviceValidate")
                        .queryParam("service", serviceUrl)
                        .queryParam("ticket", ticket)
                        .build()
                        .encode()
                        .toUriString())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        Element failure = firstElement(document, "authenticationFailure");
        if (failure != null) {
            throw new IllegalStateException(failure.getTextContent());
        }
        Element success = firstElement(document, "authenticationSuccess");
        if (success == null) {
            throw new IllegalStateException("CAS authenticationSuccess not found");
        }
        String username = textOfFirst(success, "user");
        Map<String, String> attributes = new ConcurrentHashMap<String, String>();
        Element attrs = firstElement(success, "attributes");
        if (attrs != null) {
            NodeList children = attrs.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node instanceof Element) {
                    String name = node.getLocalName();
                    if (!StringUtils.hasText(name)) {
                        name = node.getNodeName();
                        int colon = name.indexOf(':');
                        if (colon >= 0) {
                            name = name.substring(colon + 1);
                        }
                    }
                    attributes.put(name, node.getTextContent());
                }
            }
        }
        return new CasProfile(username, attributes);
    }

    private String firstText(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            String value = node.path(name).asText(null);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String firstText(Map<String, String> values, String... names) {
        if (values == null) {
            return null;
        }
        for (String name : names) {
            String value = values.get(name);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String buildCasServiceUrl(String state) {
        return UriComponentsBuilder.fromHttpUrl(redirectUri)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    private Element firstElement(Node node, String localName) {
        if (node instanceof Element && localName.equals(node.getLocalName())) {
            return (Element) node;
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Element result = firstElement(children.item(i), localName);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private String textOfFirst(Node node, String localName) {
        Element element = firstElement(node, localName);
        return element == null ? null : element.getTextContent();
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static class CallbackResult {
        private final boolean success;
        private final String callbackUrl;
        private final AuthDtos.LoginResponse loginResponse;
        private final String error;

        private CallbackResult(boolean success, String callbackUrl, AuthDtos.LoginResponse loginResponse, String error) {
            this.success = success;
            this.callbackUrl = callbackUrl;
            this.loginResponse = loginResponse;
            this.error = error;
        }

        public static CallbackResult success(String callbackUrl, AuthDtos.LoginResponse loginResponse) {
            return new CallbackResult(true, callbackUrl, loginResponse, null);
        }

        public static CallbackResult failure(String callbackUrl, String error) {
            return new CallbackResult(false, callbackUrl, null, error);
        }
    }

    private static class CasProfile {
        private final String username;
        private final Map<String, String> attributes;

        private CasProfile(String username, Map<String, String> attributes) {
            this.username = username;
            this.attributes = attributes;
        }
    }
}
