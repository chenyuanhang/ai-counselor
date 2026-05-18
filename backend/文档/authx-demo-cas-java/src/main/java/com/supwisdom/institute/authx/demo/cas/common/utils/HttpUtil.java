package com.supwisdom.institute.authx.demo.cas.common.utils;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
//import org.springframework.http.HttpMethod;
//import org.springframework.http.MediaType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
//import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 
 * @author loie
 *
 */
public class HttpUtil {

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HttpUtil.class);

  private static final int MAX_CONNECTIONS = 200;
  private static final int MAX_CONNECTIONS_PER_ROUTE = 20;

  private static final HttpClient HTTP_CLIENT = HttpClientBuilder.create().setMaxConnTotal(MAX_CONNECTIONS).setMaxConnPerRoute(MAX_CONNECTIONS_PER_ROUTE).build();

  /**
   * Execute http response.
   *
   * @param url
   *          the url
   * @param method
   *          the method
   * @param basicAuthUsername
   *          the basic auth username
   * @param basicAuthPassword
   *          the basic auth password
   * @return the http response
   */
  public static HttpResponse execute(final String url, final String method, final String basicAuthUsername, final String basicAuthPassword) {
    return execute(url, method, basicAuthUsername, basicAuthPassword, new HashMap<>(), new HashMap<>());
  }

  /**
   * Execute http response.
   *
   * @param url
   *          the url
   * @param method
   *          the method
   * @param basicAuthUsername
   *          the basic auth username
   * @param basicAuthPassword
   *          the basic auth password
   * @param headers
   *          the headers
   * @return the http response
   */
  public static HttpResponse execute(final String url, final String method, final String basicAuthUsername, final String basicAuthPassword, final Map<String, Object> headers) {
    return execute(url, method, basicAuthUsername, basicAuthPassword, new HashMap<>(), headers);
  }

  /**
   * Execute http response.
   *
   * @param url
   *          the url
   * @param method
   *          the method
   * @return the http response
   */
  public static HttpResponse execute(final String url, final String method) {
    return execute(url, method, null, null, new HashMap<>(), new HashMap<>());
  }

  /**
   * Execute http response.
   *
   * @param url
   *          the url
   * @param method
   *          the method
   * @param basicAuthUsername
   *          the basic auth username
   * @param basicAuthPassword
   *          the basic auth password
   * @param parameters
   *          the parameters
   * @param headers
   *          the headers
   * @return the http response
   */
  public static HttpResponse execute(final String url, final String method, final String basicAuthUsername, final String basicAuthPassword, final Map<String, Object> parameters, final Map<String, Object> headers) {
    return execute(url, method, basicAuthUsername, basicAuthPassword, parameters, headers, null);
  }

  /**
   * Execute http request and produce a response.
   *
   * @param url
   *          the url
   * @param method
   *          the method
   * @param basicAuthUsername
   *          the basic auth username
   * @param basicAuthPassword
   *          the basic auth password
   * @param parameters
   *          the parameters
   * @param headers
   *          the headers
   * @param entity
   *          the entity
   * @return the http response
   */
  public static HttpResponse execute(final String url, final String method, final String basicAuthUsername, final String basicAuthPassword, final Map<String, Object> parameters, final Map<String, Object> headers, final String entity) {
    try {
      final URI uri = buildHttpUri(url, parameters);
      final HttpUriRequest request;
      switch (method.toLowerCase()) {
      case "post":
        request = new HttpPost(uri);
        if (StringUtils.isNotBlank(entity)) {
          final StringEntity stringEntity = new StringEntity(entity, ContentType.APPLICATION_JSON);
          ((HttpPost) request).setEntity(stringEntity);
        }
        break;
      case "delete":
        request = new HttpDelete(uri);
        break;
      case "get":
      default:
        request = new HttpGet(uri);
        break;
      }
      headers.forEach((k, v) -> request.addHeader(k, v.toString()));
      prepareHttpRequest(request, basicAuthUsername, basicAuthPassword, parameters);
      return HTTP_CLIENT.execute(request);  // TODO: 这里可能存在 https 证书检测异常问题，自行处理
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    }
    return null;
  }

  /**
   * Close the response.
   *
   * @param response
   *          the response to close
   */
  public static void close(final HttpResponse response) {
    if (response != null) {
      final CloseableHttpResponse closeableHttpResponse = (CloseableHttpResponse) response;
      try {
        closeableHttpResponse.close();
      } catch (final IOException e) {
        log.error(e.getMessage(), e);
      }
    }
  }

  /**
   * Execute get http response.
   *
   * @param url
   *          the url
   * @param basicAuthUsername
   *          the basic auth username
   * @param basicAuthPassword
   *          the basic auth password
   * @param parameters
   *          the parameters
   * @return the http response
   */
  public static HttpResponse executeGet(final String url, final String basicAuthUsername, final String basicAuthPassword, final Map<String, Object> parameters) {
    try {
      return executeGet(url, basicAuthUsername, basicAuthPassword, parameters, new HashMap<>());
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    }
    return null;
  }

  /**
   * Execute get http response.
   *
   * @param url
   *          the url
   * @param basicAuthUsername
   *          the basic auth username
   * @param basicAuthPassword
   *          the basic auth password
   * @param parameters
   *          the parameters
   * @param headers
   *          the headers
   * @return the http response
   */
  public static HttpResponse executeGet(final String url, final String basicAuthUsername, final String basicAuthPassword, final Map<String, Object> parameters, final Map<String, Object> headers) {
    try {
      return execute(url, "GET", basicAuthUsername, basicAuthPassword, parameters, headers);
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    }
    return null;
  }

  /**
   * Execute get http response.
   *
   * @param url
   *          the url
   * @param parameters
   *          the parameters
   * @return the http response
   */
  public static HttpResponse executeGet(final String url, final Map<String, Object> parameters) {
    try {
      return executeGet(url, null, null, parameters);
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    }
    return null;
  }

  /**
   * Execute get http response.
   *
   * @param url
   *          the url
   * @return the http response
   */
  public static HttpResponse executeGet(final String url) {
    try {
      return executeGet(url, null, null, new LinkedHashMap<>());
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    }
    return null;
  }

  /**
   * Execute get http response.
   *
   * @param url
   *          the url
   * @param basicAuthUsername
   *          the basic auth username
   * @param basicAuthPassword
   *          the basic auth password
   * @return the http response
   */
  public static HttpResponse executeGet(final String url, final String basicAuthUsername, final String basicAuthPassword) {
    try {
      return executeGet(url, basicAuthUsername, basicAuthPassword, new HashMap<>());
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    }
    return null;
  }

  /**
   * Execute post http response.
   *
   * @param url
   *          the url
   * @param basicAuthUsername
   *          the basic auth username
   * @param basicAuthPassword
   *          the basic auth password
   * @param jsonEntity
   *          the json entity
   * @return the http response
   */
  public static HttpResponse executePost(final String url, final String basicAuthUsername, final String basicAuthPassword, final String jsonEntity) {
    return executePost(url, basicAuthUsername, basicAuthPassword, jsonEntity, new HashMap<>());
  }

  /**
   * Execute post http response.
   *
   * @param url
   *          the url
   * @param jsonEntity
   *          the json entity
   * @param parameters
   *          the parameters
   * @return the http response
   */
  public static HttpResponse executePost(final String url, final String jsonEntity, final Map<String, Object> parameters) {
    return executePost(url, null, null, jsonEntity, parameters);
  }

  /**
   * Execute post http response.
   *
   * @param url
   *          the url
   * @param basicAuthUsername
   *          the basic auth username
   * @param basicAuthPassword
   *          the basic auth password
   * @param jsonEntity
   *          the json entity
   * @param parameters
   *          the parameters
   * @return the http response
   */
  public static HttpResponse executePost(final String url, final String basicAuthUsername, final String basicAuthPassword, final String jsonEntity, final Map<String, Object> parameters) {
    try {
      return execute(url, "POST", basicAuthUsername, basicAuthPassword, parameters, new HashMap<>(), jsonEntity);
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    }
    return null;
  }

  /**
   * Prepare http request. Tries to set the authorization header in cases where the URL endpoint does not actually produce the header on its own.
   *
   * @param request
   *          the request
   * @param basicAuthUsername
   *          the basic auth username
   * @param basicAuthPassword
   *          the basic auth password
   * @param parameters
   *          the parameters
   */
  private static void prepareHttpRequest(final HttpUriRequest request, final String basicAuthUsername, final String basicAuthPassword, final Map<String, Object> parameters) {
    if (StringUtils.isNotBlank(basicAuthUsername) && StringUtils.isNotBlank(basicAuthPassword)) {
      String basicAuth = basicAuthUsername + ":" + basicAuthPassword;
      final String auth = Base64.encodeBase64String(basicAuth.getBytes(StandardCharsets.UTF_8));
      request.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth);
    }
  }

  private static URI buildHttpUri(final String url, final Map<String, Object> parameters) throws URISyntaxException {
    final URIBuilder uriBuilder = new URIBuilder(url);
    parameters.forEach((k, v) -> uriBuilder.addParameter(k, v.toString()));
    return uriBuilder.build();
  }

  /**
   * Create headers org . springframework . http . http headers.
   *
   * @param basicAuthUser
   *          the basic auth user
   * @param basicAuthPassword
   *          the basic auth password
   * @return the org . springframework . http . http headers
   */
  // public static org.springframework.http.HttpHeaders createBasicAuthHeaders(final String basicAuthUser, final String basicAuthPassword) {
  // final org.springframework.http.HttpHeaders acceptHeaders = new org.springframework.http.HttpHeaders();
  // acceptHeaders.setAccept(CollectionUtils.wrap(MediaType.APPLICATION_JSON));
  // if (StringUtils.isNotBlank(basicAuthUser) && StringUtils.isNotBlank(basicAuthPassword)) {
  // final String authorization = basicAuthUser + ':' + basicAuthPassword;
  // final String basic = EncodingUtils.encodeBase64(authorization.getBytes(Charset.forName("US-ASCII")));
  // acceptHeaders.set("Authorization", "Basic " + basic);
  // }
  // return acceptHeaders;
  // }
  
  
  /**
   * 解析 HttpResponse，
   * 解析完成后，会关闭
   * @param httpResponse
   * @return
   */
  public static String parseHttpResponse(HttpResponse httpResponse) {
    try {
      if (httpResponse != null) {
        StringBuilder entityStringBuilder = new StringBuilder();
  
        BufferedReader b = new BufferedReader(new InputStreamReader(httpResponse.getEntity().getContent(), "UTF-8"), 8*1024);
  
        String line=null;
        while ((line=b.readLine())!=null) {
          entityStringBuilder.append(line);
        }
        log.debug("Fetch response [{}]", entityStringBuilder.toString());
        
        return entityStringBuilder.toString();
      }
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (UnsupportedOperationException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      close(httpResponse);
    }
    
    return null;
  }

}
