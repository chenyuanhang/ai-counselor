package com.supwisdom.institute.authx.demo.cas.sso.web;

import java.io.IOException;
import java.net.URLEncoder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.supwisdom.institute.authx.demo.cas.common.utils.CasUtil;
import com.supwisdom.institute.authx.demo.cas.common.utils.CasUtil.CasUser;

@Controller
public class SSOController {

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SSOController.class);

  @Value("${cas.server.url}")
  private String casServerUrl;

  @Value("${app.server.url}")
  private String appServerUrl;
  
  
  /**
   * 业务逻辑： </br>
   * 1. 当请求系统某个页面时，若未登录，则重定向跳转到 ${app.server.url}/sso/login?returnUrl=/ </br>
   * 2. 该方法接收到请求后，调用 CasUtil.login ，判断是否存在请求参数 ticket，若不存在，将重定向跳转到 CAS认证进行登录 </br>
   * 3. CAS认证 完成登录后，仍会 重定向返回到 该地址 ${app.server.url}/sso/login?returnUrl=/&ticket=ST-1-abc-xxx </br>
   * 4. 该方法再次接收到请求后，调用 CasUtil.login ，判断是否存在请求参数 ticket，若存在，则进行 票据（ticket）校验 </br>
   * 5. 票据校验成功，将返回 CasUser对象； 票据校验失败，将返回 空的CasUser对象（即 casUser.isEmpty() == true ） </br>
   * 6. 将 CasUser对象 放入 Session </br>
   * 7. 重定向返回到 returnUrl </br>
   * 
   * 使用（可以配合 Filter）： </br>
   * 1. 登录，请求 ${app.server.url}/sso/login?returnUrl=/ </br>
   * 2. 登录成功，从 Session 中获取 casUser </br>
   * 3. 处理后续系统的鉴权逻辑 </br>
   * 
   * @param returnUrl 登录完成后，返回的地址（https://abc.com/index）、路径（/index）；建议将 returnUrl 进行 url encode
   * @param request
   * @param response
   * @throws IOException
   */
  @GetMapping(path = "/sso/login")
  public void login(
      @RequestParam(name = "returnUrl", required = true) String returnUrl,
      HttpServletRequest request, HttpServletResponse response) throws IOException {

    String service = appServerUrl + "/sso/login";

//    String returnUrl = request.getParameter("returnUrl");
    if (StringUtils.isNotBlank(returnUrl)) {
      service += "?returnUrl=" + URLEncoder.encode(returnUrl, "UTF-8");
    }
    
    CasUser casUser = CasUtil.login(casServerUrl, service, request, response);
    if (casUser == null) {
      log.info("redirect, cas login.");
      return;
    }
    
    if (casUser.isEmpty()) {
      log.error("fail, cas serviceValidate fail.");
      return;
    }
    
    request.getSession().setAttribute("casUser", casUser);
    
    if (!returnUrl.startsWith("http://") && !returnUrl.startsWith("https://")) {
      returnUrl = appServerUrl+returnUrl;
    }
    log.debug("redirect, returnUrl is {}", returnUrl);
    response.sendRedirect(returnUrl);
    return;
  }
  
  /**
   * 业务逻辑： </br>
   * 1. 系统需要注销时，请求，或重定向跳转到 ${app.server.url}/sso/logout?returnUrl=/ </br>
   * 2. 该方法接收到请求后，调用 CasUtil.logout ，判断是否存在请求参数 logout，若不存在，将重定向跳转到 CAS认证进行注销 </br>
   * 3. CAS认证 完成注销后，仍会 重定向返回到 该地址 ${app.server.url}/sso/logout?returnUrl=/&logout=logout </br>
   * 4. 该方法再次接收到请求后，调用 CasUtil.logout ，判断是否存在请求参数 logout，则返回 true </br>
   * 5. 将 CasUser对象 从 Session 删除 </br>
   * 6. 重定向返回到 returnUrl </br>
   * 
   * 使用： </br>
   * 1. 注销，请求 ${app.server.url}/sso/logout?returnUrl=/ </br>
   * 
   * @param returnUrl 登录完成后，返回的地址（https://abc.com/index）、路径（/index）；建议将 returnUrl 进行 url encode
   * @param request
   * @param response
   * @throws IOException
   */
  @GetMapping(path = "/sso/logout")
  public void logout(
      @RequestParam(name = "returnUrl", required = true) String returnUrl,
      HttpServletRequest request, HttpServletResponse response) throws IOException {
    
    String service = appServerUrl + "/sso/logout";

//    String returnUrl = request.getParameter("returnUrl");
    if (StringUtils.isNotBlank(returnUrl)) {
      service += "?returnUrl=" + URLEncoder.encode(returnUrl, "UTF-8");
    }
    
    boolean isLogout = CasUtil.logout(casServerUrl, service, request, response);
    if (!isLogout) {
      log.info("redirect, cas logout.");
      return;
    }
    
    request.getSession().removeAttribute("casUser");
    request.getSession().invalidate();
    
    if (!returnUrl.startsWith("http://") && !returnUrl.startsWith("https://")) {
      returnUrl = appServerUrl+returnUrl;
    }
    log.debug("redirect, returnUrl is {}", returnUrl);
    response.sendRedirect(returnUrl);
    return;
  }
  
  /**
   * 依赖的认证版本：1.2.10-SNAPSHOT，1.3.6-SNAPSHOT，1.4.5-SNAPSHOT，1.5.2-SNAPSHOT
   * @param request
   * @param response
   * @return
   * @throws IOException
   */
  @PostMapping(path = "/sso/userOnlineDetect", produces = "application/json;charset=UTF-8")
  @ResponseBody
  public String userOnlineDetect(
      HttpServletRequest request, HttpServletResponse response) throws IOException {
    
    boolean isAlive = false;
    if (request.getSession().getAttribute("casUser") != null) {
      CasUser casUser = (CasUser) request.getSession().getAttribute("casUser");
      isAlive = CasUtil.userOnlineDetect(casServerUrl, casUser);
    }
    
    if (isAlive) {
      return "{\"code\": 0, \"message\": null, \"data\": {\"isAlive\": true}}";
    } else {
      return "{\"code\": 0, \"message\": null, \"data\": {\"isAlive\": false}}";
    }
  }
  
  /**
   * 单点注销，本地处理逻辑 </br>
   * 
   * 业务逻辑： </br>
   * 1. 接收到请求后，将 Session invalidate
   * 2. 响应 jsonp 回调
   * 
   * 使用： </br>
   * 1. 将该地址 ${app.server.url}/sso/slo 配置到认证对接配置 中的 单点注销地址
   * 
   * @param callback
   * @param request
   * @param response
   * @return
   */
  @GetMapping(path = "/sso/slo", produces = "application/javascript;charset=UTF-8")
  @ResponseBody
  public String slo(
      @RequestParam(name = "callback", required = false) String callback,
      HttpServletRequest request, HttpServletResponse response) {
    
    request.getSession().invalidate();
    
    if (StringUtils.isNotBlank(callback)) {
      return callback+"({\"code\": 0, \"message\": null, \"data\": {\"success\": \"注销成功\"}});";
    } else {
      return "{\"code\": 0, \"message\": null, \"data\": {\"success\": \"注销成功\"}}";
    }
  }
  
}
