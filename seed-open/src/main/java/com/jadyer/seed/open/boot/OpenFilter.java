package com.jadyer.seed.open.boot;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.jadyer.seed.comm.constant.CodeEnum;
import com.jadyer.seed.comm.constant.CommResult;
import com.jadyer.seed.comm.constant.SeedConstants;
import com.jadyer.seed.comm.exception.SeedException;
import com.jadyer.seed.comm.util.BeanUtil;
import com.jadyer.seed.comm.util.CodecUtil;
import com.jadyer.seed.comm.util.JadyerUtil;
import com.jadyer.seed.comm.util.LogUtil;
import com.jadyer.seed.comm.util.RequestUtil;
import com.jadyer.seed.open.model.ReqData;
import com.jadyer.seed.open.model.RespData;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * 开放平台Filter
 * <p>
 *     负责解析请求参数以及加解密等操作
 * </p>
 * Created by 玄玉<https://jadyer.cn/> on 2016/5/8 19:34.
 */
public class OpenFilter extends OncePerRequestFilter {
    private static final int TIMESTAMP_VALID_MILLISECONDS = 1000 * 60 * 10; //时间戳验证：服务端允许客户端请求最大时间误差为10分钟
    private final String filterURL;
    private final Map<String, String> appsecretMap;
    private final Map<String, List<String>> apiGrantMap;

    /**
     * @param _filterURL    指定该Filter只拦截哪种请求URL，空表示都不拦截
     * @param _apiGrantMap  为各个appid初始化API授权情况
     * @param _appsecretMap 为各个appid初始化appsecret
     */
    OpenFilter(String filterURL, Map<String, List<String>> apiGrantMap, Map<String, String> appsecretMap){
        this.filterURL = filterURL;
        this.apiGrantMap = apiGrantMap;
        this.appsecretMap = appsecretMap;
    }


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return StringUtils.isBlank(filterURL) || !request.getServletPath().startsWith(filterURL);
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        ReqData reqData;
        String respDataStr;
        String reqIp = RequestUtil.getClientIP(request);
        long startTime = System.currentTimeMillis();
        try{
            //将请求入参解析到ReqData
            if(StringUtils.isNotBlank(request.getParameter("method")) && StringUtils.endsWithAny(request.getParameter("method"), "h5", "file.upload")){
                LogUtil.getLogger().debug("收到客户端IP=[{}]的请求报文为-->{}", reqIp, JadyerUtil.buildStringFromMap(request.getParameterMap()));
                reqData = BeanUtil.requestToBean(request, ReqData.class);
            }else{
                String reqDataStr = IOUtils.toString(request.getInputStream(), StandardCharsets.UTF_8);
                LogUtil.getLogger().debug("收到客户端IP=[{}]的请求报文为-->[{}]", reqIp, reqDataStr);
                if(StringUtils.isBlank(reqDataStr)){
                    throw new SeedException(CodeEnum.OPEN_FORM_ILLEGAL.getCode(), "请求无数据");
                }
                reqData = JSON.parseObject(reqDataStr, ReqData.class);
            }
            //验证请求方法名非空
            if(StringUtils.isBlank(reqData.getMethod())){
                throw new SeedException(CodeEnum.OPEN_UNKNOWN_METHOD.getCode(), String.format("%s-->[%s]", CodeEnum.OPEN_UNKNOWN_METHOD.getMsg(), reqData.getMethod()));
            }
            //验证时间戳
            this.verifyTimestamp(reqData.getTimestamp());
            //识别合作方
            String appsecret = appsecretMap.get(reqData.getAppid());
            LogUtil.getLogger().debug("通过appid=[{}]读取到合作方密钥{}", reqData.getAppid(), appsecret);
            if(appsecretMap.isEmpty() || StringUtils.isBlank(appsecret)){
                throw new SeedException(CodeEnum.OPEN_UNKNOWN_APPID);
            }
            //获取协议版本
            if(!StringUtils.equalsAny(reqData.getVersion(), SeedConstants.OPEN_VERSION_21, SeedConstants.OPEN_VERSION_22)){
                throw new SeedException(CodeEnum.OPEN_UNKNOWN_VERSION);
            }
            //验证接口是否已授权
            this.verifyGrant(reqData.getAppid(), reqData.getMethod());
            //验签
            //if(SeedConstants.VERSION_20.equals(reqData.getVersion())){
            //    this.verifySign(request.getParameterMap(), apiApplication.getAppSecret());
            //    filterChain.doFilter(request, response);
            //}
            //解密并处理（返回诸如html或txt内容时，就不用先得到字符串再转成字节数组输出，这会影响性能，尤其对账文件下载）
            RequestParameterWrapper requestWrapper = new RequestParameterWrapper(request);
            requestWrapper.addAllParameters(this.decrypt(reqData, appsecret));
            if(StringUtils.endsWithAny(reqData.getMethod(), "h5", "agree", "download")){
                filterChain.doFilter(requestWrapper, response);
                respDataStr = reqData.getMethod() + "...";
                LogUtil.getLogger().info("返回客户端IP=[{}]的应答明文为-->[{}]，Duration[{}]ms", reqIp, respDataStr, (System.currentTimeMillis()-startTime));
            }else{
                ResponseContentWrapper responseWrapper = new ResponseContentWrapper(response);
                filterChain.doFilter(requestWrapper, responseWrapper);
                respDataStr = responseWrapper.getContent();
                LogUtil.getLogger().info("返回客户端IP=[{}]的应答明文为-->[{}]", reqIp, respDataStr);
                RespData respData = JSON.parseObject(respDataStr, RespData.class);
                if(CodeEnum.SUCCESS.getCode() == Integer.parseInt(respData.getCode())){
                    if(SeedConstants.OPEN_VERSION_21.equals(reqData.getVersion())){
                        respData.setData(StringUtils.isBlank(respData.getData()) ? "" : CodecUtil.aesEncrypt(respData.getData(), appsecret));
                    }else{
                        Map<String, String> dataMap = JSON.parseObject(appsecret, new TypeReference<Map<String, String>>(){});
                        respData.setSign(StringUtils.isBlank(respData.getData()) ? "" : CodecUtil.rsaSign(respData.getData(), dataMap.get("openPrivateKey")));
                        respData.setData(StringUtils.isBlank(respData.getData()) ? "" : CodecUtil.rsaEncrypt(respData.getData(), dataMap.get("publicKey")));
                    }
                }
                String respDataJson = JSON.toJSONString(respData);
                LogUtil.getLogger().debug("返回客户端IP=[{}]的应答密文为-->[{}]，Duration[{}]ms", reqIp, respDataJson, (System.currentTimeMillis()-startTime));
                RequestUtil.writeToResponse(respDataJson, response);
            }
        }catch(SeedException e){
            respDataStr = JSON.toJSONString(CommResult.fail(e.getCode(), e.getMessage()), true);
            LogUtil.getLogger().info("返回客户端IP=[{}]的应答明文为-->[{}]，Duration[{}]ms", reqIp, respDataStr, (System.currentTimeMillis()-startTime));
            RequestUtil.writeToResponse(respDataStr, response);
        }
    }


    /**
     * 验证时间戳
     */
    private void verifyTimestamp(String timestamp){
        if(StringUtils.isBlank(timestamp)){
            throw new SeedException(CodeEnum.SYSTEM_BUSY.getCode(), "timestamp is blank");
        }
        try {
            long reqTime = DateUtils.parseDate(timestamp, SeedConstants.OPEN_TIMESTAMP).getTime();
            if(Math.abs(System.currentTimeMillis()-reqTime) >= TIMESTAMP_VALID_MILLISECONDS){
                throw new SeedException(CodeEnum.OPEN_TIMESTAMP_ERROR);
            }
        } catch (ParseException e) {
            throw new SeedException(CodeEnum.SYSTEM_BUSY.getCode(), "timestamp is invalid");
        }
    }


    /**
     * 验证接口是否授权
     */
    private void verifyGrant(String appid, String method){
        boolean isGrant = false;
        for (Map.Entry<String, List<String>> entry : apiGrantMap.entrySet()) {
            if(appid.equals(entry.getKey())){
                if(entry.getValue().contains(method)){
                    isGrant = true;
                    break;
                }
            }
        }
        if(!isGrant){
            throw new SeedException(CodeEnum.OPEN_UNGRANT_API.getCode(), "未授权的接口-->["+method+"]");
        }
    }


    /**
     * 验签
     */
    @SuppressWarnings("unused")
    private void verifySign(Map<String, String[]> paramMap, String appsecret){
        String signType = paramMap.get("signType")[0];
        if(!SeedConstants.OPEN_SIGN_TYPE_md5.equals(signType) && !SeedConstants.OPEN_SIGN_TYPE_hmac.equals(signType)){
            throw new SeedException(CodeEnum.OPEN_UNKNOWN_SIGN);
        }
        StringBuilder sb = new StringBuilder();
        List<String> keys = new ArrayList<>(paramMap.keySet());
        Collections.sort(keys);
        for(String key : keys){
            String[] value = paramMap.get(key);
            if("sign".equalsIgnoreCase(key)){
                continue;
            }
            sb.append(key).append(value[0]);
        }
        boolean verfiyResult;
        if(SeedConstants.OPEN_SIGN_TYPE_md5.equals(signType)){
            String data = sb.append(appsecret).toString();
            String sign = DigestUtils.md5Hex(data);
            LogUtil.getLogger().debug("请求参数签名原文-->[{}]", data);
            LogUtil.getLogger().debug("请求参数签名得到-->[{}]", sign);
            verfiyResult = sign.equals(paramMap.get("sign")[0]);
        }else{
            String sign = CodecUtil.buildHmacSign(sb.toString(), appsecret, "HmacMD5");
            LogUtil.getLogger().debug("请求参数签名原文-->[{}]", sb);
            LogUtil.getLogger().debug("请求参数签名得到-->[{}]", sign);
            verfiyResult = sign.equals(paramMap.get("sign")[0]);
        }
        if(!verfiyResult){
            throw new SeedException(CodeEnum.OPEN_SIGN_ERROR);
        }
    }


    /**
     * 解密
     * <ul>
     *     <li>2.1--AES--直接解密</li>
     *     <li>2.2--RSA--公钥加密，私钥解密--私钥签名，公钥验签</li>
     * </ul>
     */
    private Map<String, Object> decrypt(ReqData reqData, String appsecret){
        if(StringUtils.isBlank(reqData.getData())){
            throw new SeedException(CodeEnum.SYSTEM_BUSY.getCode(), "data is blank");
        }
        String dataPlain;
        if(SeedConstants.OPEN_VERSION_21.equals(reqData.getVersion())){
            dataPlain = CodecUtil.aesDecrypt(reqData.getData(), appsecret);
        }else{
            //appsecret={"publicKey":"合作方公钥","openPublicKey":"我方公钥","openPrivateKey":"我方私钥"}
            Map<String, String> dataMap = JSON.parseObject(appsecret, new TypeReference<Map<String, String>>(){});
            dataPlain = CodecUtil.rsaDecrypt(reqData.getData(), dataMap.get("openPrivateKey"));
            if(!CodecUtil.rsaVerify(dataPlain, dataMap.get("publicKey"), reqData.getSign())){
                throw new SeedException(CodeEnum.OPEN_SIGN_ERROR);
            }
        }
        LogUtil.getLogger().info("请求参数解密得到dataPlain=[{}]", dataPlain);
        reqData.setData(dataPlain);
        Map<String, Object> allParams = new HashMap<>();
        for(Field field : reqData.getClass().getDeclaredFields()){
            if(!"serialVersionUID".equals(field.getName())){
                String methodName = "get" + StringUtils.capitalize(field.getName());
                Object fieldValue;
                try {
                    fieldValue = reqData.getClass().getDeclaredMethod(methodName).invoke(reqData);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                allParams.put(field.getName(), fieldValue);
            }
        }
        return allParams;
    }


    /**
     * 解决request-body只能读取一次的问题
     * ---------------------------------------------------------------------------------------
     * 适用于：请求进入业务逻辑前，针对request-body做一些前置处理
     * ---------------------------------------------------------------------------------------
     */
    private static class RequestBodyWrapper extends HttpServletRequestWrapper {
        private final byte[] requestBody;
        public RequestBodyWrapper (HttpServletRequest request) {
            super(request);
            String requestBodyStr = RequestUtil.extractHttpServletRequestBodyMessage(request);
            this.requestBody = StringUtils.isEmpty(requestBodyStr) ? new byte[0] : requestBodyStr.getBytes(StandardCharsets.UTF_8);
        }
        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream()));
        }
        @Override
        public ServletInputStream getInputStream() {
            final ByteArrayInputStream bais = new ByteArrayInputStream(requestBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return false;
                }
                @Override
                public boolean isReady() {
                    return false;
                }
                @Override
                public void setReadListener(ReadListener listener) {}
                @Override
                public int read() {
                    return bais.read();
                }
            };
        }
    }


    /**
     * 可手工设置HttpServletRequest入参的Wrapper
     * ---------------------------------------------------------------------------------------
     * 由于HttpServletRequest.getParameterMap()得到的Map是immutable的，不可更改的
     * 而且HttpServletRequest.setAttribute()方法也是不能修改请求参数的，故扩展此类
     * ---------------------------------------------------------------------------------------
     * RequestParameterWrapper requestWrapper = new RequestParameterWrapper(request);
     * Map<String, Object> allParams = new HashMap<>();
     * allParams.put("appid", "101");
     * allParams.put("data", "{\"name\":\"玄玉<https://jadyer.cn/>\"}");
     * requestWrapper.addAllParameters(allParams);
     * Map<String, String> allHeaders = new HashMap<>();
     * allHeaders.put("From-SYS", "seed-test");
     * allHeaders.put("From-Appid", "101");
     * requestWrapper.addAllHeaders(allHeaders);
     * filterChain.doFilter(requestWrapper, response);
     * ---------------------------------------------------------------------------------------
     */
    private static class RequestParameterWrapper extends HttpServletRequestWrapper {
        private final Map<String, String[]> paramMap = new HashMap<>();
        private final Map<String, String> headerMap = new HashMap<>();
        RequestParameterWrapper(HttpServletRequest request) {
            super(request);
            this.paramMap.putAll(request.getParameterMap());
            Enumeration<String> headerNames = request.getHeaderNames();
            while(headerNames.hasMoreElements()){
                String headerName = headerNames.nextElement();
                headerMap.put(headerName, request.getHeader(headerName));
            }
        }
        @Override
        public String getParameter(String name) {
            String[] values = this.paramMap.get(name);
            if(null==values || values.length==0){
                return "";
            }
            return values[0];
        }
        @Override
        public String[] getParameterValues(String name) {
            String[] values = this.paramMap.get(name);
            if(null==values || values.length==0){
                return new String[0];
            }
            return values;
        }
        @Override
        public Enumeration<String> getParameterNames() {
            return new Vector<>(this.paramMap.keySet()).elements();
        }
        @Override
        public Map<String, String[]> getParameterMap() {
            return this.paramMap;
        }
        void addParameter(String name, Object value){
            if(null != value){
                if(value instanceof String[]){
                    this.paramMap.put(name, (String[])value);
                }else if(value instanceof String){
                    this.paramMap.put(name, new String[]{(String)value});
                }else{
                    this.paramMap.put(name, new String[]{String.valueOf(value)});
                }
            }
        }
        void addAllParameters(Map<String, Object> allParams){
            for(Map.Entry<String,Object> entry : allParams.entrySet()){
                this.addParameter(entry.getKey(), entry.getValue());
            }
        }
        @Override
        public String getHeader(String name) {
            String value = this.headerMap.get(name);
            if(null == value){
                return "";
            }
            return value;
        }
        @Override
        public Enumeration<String> getHeaders(String name) {
            String value = this.headerMap.get(name);
            if(StringUtils.isEmpty(value)){
                return new Vector<String>().elements();
            }
            Vector<String> values = new Vector<>();
            values.add(this.getHeader(name));
            return values.elements();
        }
        @Override
        public Enumeration<String> getHeaderNames() {
            return new Vector<>(this.headerMap.keySet()).elements();
        }
        void addHeader(String name, String value){
            if(StringUtils.isNotBlank(value)){
                this.headerMap.put(name, value);
            }
        }
        void addAllHeaders(Map<String, String> allHeaders){
            for(Map.Entry<String,String> entry : allHeaders.entrySet()){
                this.addHeader(entry.getKey(), entry.getValue());
            }
        }
    }


    /**
     * 可手工设置HttpServletResponse出参的Wrapper
     * ---------------------------------------------------------------------------------------
     * ResponseContentWrapper responseWrapper = new ResponseContentWrapper(response);
     * filterChain.doFilter(request, responseWrapper);
     * String content = responseWrapper.getContent();
     * response.getOutputStream().write(content.getBytes("UTF-8"));
     * return;
     * ---------------------------------------------------------------------------------------
     * response.setHeader("Content-Type", "application/json; charset=UTF-8");
     * //response.getWriter().write("abcdefg");
     * response.getOutputStream().write(("{\"code\":\"102\", \"msg\":\"重复请求\"}").getBytes("UTF-8"));
     * return;
     * ---------------------------------------------------------------------------------------
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