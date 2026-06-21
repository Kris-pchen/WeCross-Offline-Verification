package com.traffic.wecross.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class ApiSecurityConfig extends WebSecurityConfigurerAdapter {
    private final WeCrossCredentialFilter credentialFilter;

    public ApiSecurityConfig(WeCrossCredentialFilter credentialFilter) {
        this.credentialFilter = credentialFilter;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .authorizeRequests()
                .antMatchers("/api/verification/health").permitAll()
                .anyRequest().authenticated()
                .and()
                .addFilterBefore(credentialFilter, UsernamePasswordAuthenticationFilter.class);
    }
}