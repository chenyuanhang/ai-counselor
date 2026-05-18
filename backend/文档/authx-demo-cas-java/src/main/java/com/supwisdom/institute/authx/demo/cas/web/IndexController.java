package com.supwisdom.institute.authx.demo.cas.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.supwisdom.institute.authx.demo.cas.common.utils.CasUtil.CasUser;

@Controller
public class IndexController {

  @GetMapping(path = {"/", "/index"})
  public String index(Model model,
      HttpServletRequest request, HttpServletResponse response) {
    
    if (request.getSession().getAttribute("casUser") != null) {
      CasUser casUser = (CasUser) request.getSession().getAttribute("casUser");
      model.addAttribute("casUser", casUser);
    }
    
    return "index";
  }

}
