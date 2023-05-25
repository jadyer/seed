package com.jadyer.seed.comm.constant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作码
 * Created by 玄玉<https://jadyer.cn/> on 2015/6/3 21:26.
 */
public enum CodeEnum {
    SUCCESS              (0,    "成功"),
    SYSTEM_OK            (1000, "保留码"),
    SYSTEM_BUSY          (1001, "系统繁忙"),
    SYSTEM_ERROR         (1002, "系统错误"),
    FILE_NOT_FOUND       (1003, "文件未找到"),
    FILE_TRANSFER_FAIL   (1004, "文件传输失败"),
    FTP_CANNOT_CONNECT   (1005, "FTP：无法连接服务器"),
    FTP_CONNECT_FAIL     (1006, "FTP：服务器连接失败"),
    FTP_LOGIN_FAIL       (1007, "FTP：服务器登录失败"),
    FTP_UPLOAD_FAIL      (1008, "FTP：上传文件失败"),
    FTP_DOWNLOAD_FAIL    (1009, "FTP：下载文件失败"),
    FTP_FILE_READ_FAIL   (1010, "FTP：文件读取失败"),
    OPEN_UNKNOWN_APPID   (2001, "OPEN：未知的合作方"),
    OPEN_UNKNOWN_VERSION (2002, "OPEN：未知的协议版本"),
    OPEN_UNKNOWN_SIGN    (2003, "OPEN：未知的签名算法"),
    OPEN_UNKNOWN_METHOD  (2004, "OPEN：未知的接口请求"),
    OPEN_TIMESTAMP_ERROR (2005, "OPEN：时间戳异常"),
    OPEN_SIGN_ERROR      (2006, "OPEN：验签拒绝"),
    OPEN_DECRYPT_FAIL    (2007, "OPEN：解密失败"),
    OPEN_FORM_ILLEGAL    (2008, "OPEN：表单验证未通过"),
    OPEN_UNGRANT_API     (2009, "OPEN：未授权的接口"),
    OPEN_UNGRANT_DATA    (2010, "OPEN：未授权的数据访问"),
    OPEN_APPID_NO_IMPL   (2011, "OPEN：合作方业务无实现"),
    OPEN_PROCESSING      (2012, "OPEN：请求处理中");

    private final int code;
    private final String msg;

    CodeEnum(int code, String msg){
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    /**
     * 通过枚举code获取枚举对象
     */
    public static CodeEnum getByCode(int code){
        for(CodeEnum codeEnum : values()){
            if(codeEnum.getCode() == code){
                return codeEnum;
            }
        }
        throw new IllegalArgumentException("无法识别的Code=[" + code + "]");
    }


    /**
     * 通过枚举code获取对应的msg
     */
    public static String getMsgByCode(int code){
        return getByCode(code).getMsg();
    }


    /**
     * 获取全部枚举
     */
    public List<CodeEnum> getAllEnum(){
        List<CodeEnum> dataList = new ArrayList<>();
        Collections.addAll(dataList, values());
        return dataList;
    }


    /**
     * 获取全部枚举code
     */
    public List<Integer> getAllEnumCode(){
        List<Integer> dataList = new ArrayList<>();
        for(CodeEnum codeEnum : values()){
            dataList.add(codeEnum.getCode());
        }
        return dataList;
    }
    
    
    public Map<Integer, String> getEnumMap(){
        HashMap<Integer, String> map = new HashMap<>();
        for (CodeEnum codeEnum : EnumSet.allOf(CodeEnum.class)) {
            map.put(codeEnum.code, codeEnum.msg);
        }
        return map;
    }
}