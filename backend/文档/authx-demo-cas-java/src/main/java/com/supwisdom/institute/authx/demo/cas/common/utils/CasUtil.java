package com.supwisdom.institute.authx.demo.cas.common.utils;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.alibaba.fastjson.JSONObject;

/**
 * 对接文档：https://authx-docs.dev2.supwisdom.com/authx/index.html#/guide/cas/st/guide
 * 
 * @author loie
 *
 */
public class CasUtil {
  
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CasUtil.class);
  
  public static void main(String[] args) throws UnsupportedEncodingException {
    String casServerHostUrl = "https://cas.supwisdom.com/cas";
    String service = "http://example.com/sso/login";
    String ticket = "ST-2-KnDjgoEJtWz7lyuTMoppzqYtyZkcas-server-site-webapp-6f6bf5dcf5-qt7qf";
    
    CasUser casUser = CasUtil.serviceValidate(casServerHostUrl, service, ticket);
    
    System.out.println(casUser);
  }
  
  /**
   * CAS 登录
   * 1. 判断 request 是否存在 ticket
   * 2. 若不存在，跳转到 CAS 认证的登录页面 /login，并返回 null
   * 3. 若存在，则验证 ticket 是否合法 /serviceValidate
   * 4. 验证成功，返回 CasUser；否则，验证失败，返回 CasUser.EMPTY
   * 
   * @param casServerHostUrl
   * @param service
   * @param request
   * @param response
   * @return
   * @throws IOException
   */
  public static CasUser login(String casServerHostUrl, String service,
      HttpServletRequest request, HttpServletResponse response) throws IOException {
    
    String ticket = request.getParameter("ticket");
    if (StringUtils.isBlank(ticket)) {
      String redirect = casServerHostUrl +"/login?service="+URLEncoder.encode(service, "UTF-8");
      log.debug("redirect to [{}]", redirect);
      
      response.sendRedirect(redirect);
      return null;
    }
    log.debug("ticket: {}", ticket);

    CasUser casUser = CasUtil.serviceValidate(casServerHostUrl, service, ticket);
    log.debug("casUser: {}", casUser);
    
    return casUser;
  }
  
  /**
   * CAS 注销
   * 1. 判断 request 是否存在 logout
   * 2. 若不存在，跳转到 CAS 认证的注销页面 /logout，并返回 false
   * 3. 若存在，则说明 CAS 认证 已注销成功，返回 true
   * 
   * @param casServerHostUrl
   * @param service
   * @param request
   * @param response
   * @return
   * @throws IOException
   */
  public static boolean logout(String casServerHostUrl, String service,
      HttpServletRequest request, HttpServletResponse response) throws IOException {

    String logout = request.getParameter("logout");
    if (StringUtils.isBlank(logout)) {
      if (service.indexOf("?") < 0) {
        service += "?";
      } else {
        service += "&";
      }
      service += "logout=logout";

      String redirect = casServerHostUrl +"/logout?service="+URLEncoder.encode(service, "UTF-8");
      log.debug("redirect to [{}]", redirect);

      response.sendRedirect(redirect);
      return false;
    }

    return true;
  }
  
  public static CasUser serviceValidate(String casServerHostUrl, String service, String ticket) throws UnsupportedEncodingException {
    String serviceValidateUrl = casServerHostUrl + "/serviceValidate" +"?service="+URLEncoder.encode(service, "UTF-8") +"&ticket="+ticket;
    
    HttpResponse httpResponse = HttpUtil.execute(serviceValidateUrl, "GET");  // TODO: 这里可能存在 https 证书检测异常问题，自行处理
    
    String responseXml = HttpUtil.parseHttpResponse(httpResponse); log.debug(responseXml);
    
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    try {
      DocumentBuilder db = dbf.newDocumentBuilder();
      Document document = db.parse(new InputSource(new StringReader(responseXml)));
      
      // 票据验证失败
      NodeList authenticationFailureNodeList = document.getElementsByTagName("cas:authenticationFailure");
      if (authenticationFailureNodeList != null && authenticationFailureNodeList.getLength() > 0) {
        String authenticationFailureText = authenticationFailureNodeList.item(0).getTextContent();
        
        // TODO: 票据验证失败
        log.error("serviceValidate failed: [{}]", authenticationFailureText);
        return CasUser.EMPTY;
      }
      
      // 票据验证成功
      NodeList authenticationSuccessNodeList = document.getElementsByTagName("cas:authenticationSuccess");
      if (authenticationSuccessNodeList != null && authenticationSuccessNodeList.getLength() > 0) {
        CasUser casUser = new CasUser();
        casUser.setService(service);
        casUser.setTicket(ticket);
        
        Node authenticationSuccessNode = authenticationSuccessNodeList.item(0);
        Element ele = (Element) authenticationSuccessNode;
        
        NodeList userNodeList = ele.getElementsByTagName("cas:user");
        if (userNodeList != null && userNodeList.getLength() > 0) {
          String userText = userNodeList.item(0).getTextContent();
          
          casUser.setUser(userText);
        }
        
        NodeList attributesNodeList = ele.getElementsByTagName("cas:attributes");
        if (attributesNodeList != null && attributesNodeList.getLength() > 0) {
          Node attributesNode = attributesNodeList.item(0);
          Element attributesEle = (Element) attributesNode;
          
          if (attributesEle.getChildNodes() != null && attributesEle.getChildNodes().getLength() > 0) {
            casUser.setAttributes(new HashMap<String, Object>());
            
            for (int i=0; i<attributesEle.getChildNodes().getLength(); i++) {
              Node attributeNode = attributesEle.getChildNodes().item(i);
              
              String name = attributeNode.getNodeName();
              String text = attributeNode.getTextContent();
              
              if (name.startsWith("cas:")) {
                casUser.getAttributes().put(name.substring(4), text==null?null:text.trim());
              }
            }
          }
        }
        
        log.debug("serviceValidate success: [{}]", casUser);
        return casUser;
      }
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    } catch (UnsupportedOperationException e) {
      e.printStackTrace();
    } catch (SAXException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    
    return CasUser.EMPTY;
  }

  
  /**
   * CAS 登录状态检测
   * 依赖的认证版本：1.2.10-SNAPSHOT，1.3.6-SNAPSHOT，1.4.5-SNAPSHOT，1.5.2-SNAPSHOT
   * 
   * @param casServerHostUrl
   * @param casUser
   * @return
   * @throws UnsupportedEncodingException
   */
  public static boolean userOnlineDetect(String casServerHostUrl, CasUser casUser) throws UnsupportedEncodingException {
    return userOnlineDetect(casServerHostUrl, casUser.getService(), casUser.getTicket(), casUser.getUser());
  }
  
  public static boolean userOnlineDetect(String casServerHostUrl, String service, String ticket, String username) throws UnsupportedEncodingException {
    
    String userOnlineDetectUrl = casServerHostUrl + "/login/userOnlineDetect" 
      +"?service="+URLEncoder.encode(service, "UTF-8") 
      +"&ticket="+ticket
      +"&username="+username;

    HttpResponse httpResponse = HttpUtil.execute(userOnlineDetectUrl, "POST");  // TODO: 这里可能存在 https 证书检测异常问题，自行处理
    
    String responseJson = HttpUtil.parseHttpResponse(httpResponse); log.debug(responseJson);
    
    /*
登录状态正常
{
  "code":0,
  "data":{
    "isAlive":true
  },
  "message":"已登录"
}
登录状态异常
{
  "code":-1,
  "message":"已过期",
  "error":{
    "error":"已过期"
  }
}
{
  "code":-1,
  "message":"已注销",
  "error":{
    "error":"已注销"
  }
}
     */
    
    JSONObject responseJSONObject = JSONObject.parseObject(responseJson);
    if (responseJSONObject != null && responseJSONObject.containsKey("code") && responseJSONObject.getIntValue("code") == 0) {
      boolean isAlive = responseJSONObject.getJSONObject("data").getBooleanValue("isAlive");
      return isAlive;
    }
    
    return false;
  }
  
  /**
   * CAS认证票据校验成功后，返回的用户信息
   * 
   * @author loie
   *
   */
  public static class CasUser  {
    
    public static final CasUser EMPTY = new CasUser();
    
    /**
     * 用户获取 CasUser 的 service
     */
    private String service;
    
    /**
     * 用户获取 CasUser 的 票据 ticket
     */
    private String ticket;

    /**
     * 账号名
     */
    private String user;
    
    /**
     * 账号属性
     * 
attribute | 说明 | 示例
- | - | -
name | 姓名 | 智慧校园管理员
accountId | 帐号ID | 1
userId | 用户ID | 1
userName | 用户姓名 | 智慧校园管理员
identityTypeId | 身份ID | 1
identityTypeCode | 身份代码 | admin
identityTypeName | 身份名称 | 管理
organizationId | 组织机构ID | 1
organizationCode | 组织机构代码 | 1
organizationName | 组织机构名称 | 智慧大学
     */
    private Map<String, Object> attributes;
    
    public boolean isEmpty() {
      return StringUtils.isBlank(getUser());
    }

    public String getService() {
      return service;
    }

    public void setService(String service) {
      this.service = service;
    }

    public String getTicket() {
      return ticket;
    }

    public void setTicket(String ticket) {
      this.ticket = ticket;
    }

    public String getUser() {
      return user;
    }

    public void setUser(String user) {
      this.user = user;
    }

    public Map<String, Object> getAttributes() {
      return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
      this.attributes = attributes;
    }

  }

}
