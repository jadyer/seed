package com.jadyer.seed.comm.constant;

/**
 * 封装接口应答报文
 * Created by 玄玉<https://jadyer.cn/> on 2015/6/3 21:57.
 */
public class CommResult<T> {
    private int code = CodeEnum.SUCCESS.getCode();
    private String msg = CodeEnum.SUCCESS.getMsg();
    private T data;

    private CommResult() {}

    private CommResult(T data) {
        this.data = data;
    }

    private CommResult(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public static <Void> CommResult<Void> fail(){
        return fail(CodeEnum.SYSTEM_ERROR.getCode(), CodeEnum.SYSTEM_ERROR.getMsg());
    }

    public static <Void> CommResult<Void> fail(CodeEnum codeEnum){
        return fail(codeEnum.getCode(), codeEnum.getMsg());
    }

    public static <Void> CommResult<Void> fail(int code, String msg){
        return new CommResult<>(code, msg);
    }

    public static <Void> CommResult<Void> success(){
        return new CommResult<>();
    }

    public static <T> CommResult<T> success(T data){
        return new CommResult<>(data);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}