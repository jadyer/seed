package com.jadyer.seed.comm.boot;

import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

//@Configuration
public class CorsFilterConfiguration {
    private CorsConfiguration corsConfig() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.addAllowedOrigin(CorsConfiguration.ALL);
        corsConfig.addAllowedHeader(CorsConfiguration.ALL);
        corsConfig.addAllowedMethod(CorsConfiguration.ALL);
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);
        return corsConfig;
    }


    @Bean
    public CorsFilter corsFilter(){
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", this.corsConfig());
        return new CorsFilter(source);
    }


    // @Bean
    // public FilterRegistrationBean<CorsFilter> corsBean(){
    //     UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    //     source.registerCorsConfiguration("/**", this.corsConfig());
    //     FilterRegistrationBean<CorsFilter> cors = new FilterRegistrationBean<>(new CorsFilter(source));
    //     cors.setName("crossOriginFilter");
    //     // 这个顺序也有可能会有影响，尽量设置在拦截器前面
    //     cors.setOrder(0);
    //     return cors;
    // }
}