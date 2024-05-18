package com.jadyer.seed.open.boot;

import com.jadyer.seed.comm.constant.CodeEnum;
import com.jadyer.seed.comm.constant.SeedConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import redis.clients.jedis.JedisCluster;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * http://www.redis.cn/commands.html
 * http://www.runoob.com/redis/redis-commands.html
 */
public class RedisFilter extends OncePerRequestFilter {
    private static final int EXPIRE_TIME_SECONDS = 60 * 12;          //12分钟过期
    private static final String REDIS_DATA_KEY = "data-key";         //请求应答内容的RedisKey
    private static final String REDIS_DATA_CONTENT = "data-content"; //请求应答内容
    private static final String RESP_CONTENT_TYPE = "application/json; charset=UTF-8";
    private final String filterURL;
    private final JedisCluster jedisCluster;
    private final List<String> filterMethodList = new ArrayList<>();

    /**
     * @param _filterURL        指定该Filter只拦截哪种请求URL，空表示都不拦截
     * @param jedisCluster      redis集群对象
     * @param _filterMethodList 指定该Filter只拦截的方法列表，空表示都不拦截
     */
    RedisFilter(String filterURL, JedisCluster jedisCluster, List<String> filterMethodList){
        this.filterURL = filterURL;
        this.jedisCluster = jedisCluster;
        this.filterMethodList.addAll(filterMethodList);
    }


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return StringUtils.isNotBlank(filterURL) || !request.getServletPath().startsWith(filterURL) || filterMethodList.isEmpty() || !filterMethodList.contains(request.getParameter("method"));
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        //计算请求唯一性的标记
        String redisKey = null;
        if(SeedConstants.OPEN_VERSION_21.equals(request.getParameter("version")) || SeedConstants.OPEN_VERSION_22.equals(request.getParameter("version"))){
            redisKey = "open-" + request.getParameter("appid") + "-" + DigestUtils.md5Hex(request.getParameter("data"));
        }
        Long initResult = jedisCluster.hsetnx(redisKey, REDIS_DATA_KEY, REDIS_DATA_CONTENT);
        //返回1表示首次请求，即此时往redisKey指定的哈希集中成功添加了字段REDIS_DATA_KEY及其值
        //此时会缓存请求的应答内容
        if(initResult == 1){
            ResponseContentWrapper wrapperResponse = new ResponseContentWrapper(response);
            filterChain.doFilter(request, wrapperResponse);
            String content = wrapperResponse.getContent();
            Map<String, String> hash = new HashMap<>();
            hash.put(REDIS_DATA_KEY, content);
            jedisCluster.hmset(redisKey, hash);
            jedisCluster.expire(redisKey, EXPIRE_TIME_SECONDS);
            response.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
            return;
        }
        //返回0表示非首次请求，此时会计算应答哪些内容
        Map<String, String> values = jedisCluster.hgetAll(redisKey);
        if(null!=values && values.containsKey(REDIS_DATA_KEY) && !REDIS_DATA_CONTENT.equals(values.get(REDIS_DATA_KEY))){
            response.setHeader("Content-Type", RESP_CONTENT_TYPE);
            response.getWriter().write(values.get(REDIS_DATA_KEY));
        }else{
            response.setHeader("Content-Type", RESP_CONTENT_TYPE);
            response.getOutputStream().write(("{\"code\":\"" + CodeEnum.SYSTEM_BUSY.getCode() + "\", \"msg\":\"处理中，请勿重复提交\"}").getBytes(StandardCharsets.UTF_8));
        }
    }


    /**
     * 可手工设置HttpServletResponse出参的Wrapper
     */
    private static class ResponseContentWrapper extends HttpServletResponseWrapper {
        private final ResponsePrintWriter writer;
        private final OutputStreamWrapper outputWrapper;
        private final ByteArrayOutputStream output;
        ResponseContentWrapper(HttpServletResponse httpServletResponse) {
            super(httpServletResponse);
            output = new ByteArrayOutputStream();
            outputWrapper = new OutputStreamWrapper(output);
            writer = new ResponsePrintWriter(output);
        }
        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            output.close();
            writer.close();
        }
        @Override
        public ServletOutputStream getOutputStream() {
            return outputWrapper;
        }
        String getContent() {
            try {
                writer.flush();
                return writer.getByteArrayOutputStream().toString(StandardCharsets.UTF_8.displayName());
            } catch (UnsupportedEncodingException e) {
                return "UnsupportedEncoding";
            }
        }
        public void close() {
            writer.close();
        }
        @Override
        public PrintWriter getWriter() {
            return writer;
        }
        private static class ResponsePrintWriter extends PrintWriter {
            ByteArrayOutputStream output;
            ResponsePrintWriter(ByteArrayOutputStream output) {
                super(output);
                this.output = output;
            }
            ByteArrayOutputStream getByteArrayOutputStream() {
                return output;
            }
        }
        private static class OutputStreamWrapper extends ServletOutputStream {
            ByteArrayOutputStream output;
            OutputStreamWrapper(ByteArrayOutputStream output) {
                this.output = output;
            }
            @Override
            public boolean isReady() {
                return true;
            }
            @Override
            public void setWriteListener(WriteListener listener) {
                throw new UnsupportedOperationException("UnsupportedMethod setWriteListener.");
            }
            @Override
            public void write(int b) {
                output.write(b);
            }
        }
    }
}