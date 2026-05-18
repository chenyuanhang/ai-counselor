package com.supwisdom.institute.authx.demo.cas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
  
  @Configuration
  public class DemoFileConfigurer extends WebMvcConfigurationSupport {
    
    @Override
    protected void addResourceHandlers(ResourceHandlerRegistry registry) {

      registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
      registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:META-INF/resources/webjars/");

      registry.addResourceHandler("/demo/**").addResourceLocations("file:/home/java-app/demo/");
      
      super.addResourceHandlers(registry);
    }
    
  }

}
