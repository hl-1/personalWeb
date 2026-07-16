package com.studystack.identity.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentitySecurityProperties.class)
public class IdentityConfiguration {
}
