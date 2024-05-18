package com.jadyer.seed.mpp.boot;

import com.jadyer.seed.comm.constant.SeedConstants;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Created by 玄玉<https://jadyer.cn/> on 2017/3/11 11:30.
 */
@Configuration
public class MenuConfiguration {
    @Bean
    public Filter menuFilter(){
        return new MenuFilter();
    }

    /**
     * 后台管理页面菜单高亮焦点设置
     * Created by 玄玉<https://jadyer.cn/> on 2017/3/11 11:32.
     */
    private static class MenuFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
            String currentSubMenu = request.getParameter(SeedConstants.WEB_CURRENT_SUB_MENU);
            if(StringUtils.isNotBlank(currentSubMenu)){
                request.setAttribute(SeedConstants.WEB_CURRENT_MENU, currentSubMenu.substring(0, currentSubMenu.length()-3));
                request.setAttribute(SeedConstants.WEB_CURRENT_SUB_MENU, currentSubMenu);
            }else{
                request.setAttribute(SeedConstants.WEB_CURRENT_MENU, "menu_mpp");
                request.setAttribute(SeedConstants.WEB_CURRENT_SUB_MENU, "menu_mpp_02");
            }
            filterChain.doFilter(request, response);
        }
    }
}