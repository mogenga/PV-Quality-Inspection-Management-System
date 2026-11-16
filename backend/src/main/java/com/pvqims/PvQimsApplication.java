package com.pvqims;

import com.pvqims.security.JwtService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * 认证由 JWT 过滤器完成，不需要 Boot 默认生成的内存用户，故排除其自动配置。
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(JwtService.JwtProperties.class)
public class PvQimsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PvQimsApplication.class, args);
    }
}
