package com.zjw.ocr.vo;

/**
 * Json结果体
 * @author carway
 * @date 2018/7/17.
 */
public class ResultObject {
    private String msg;
    private int code;
    private Object data;
    private boolean success;
    private String error;

    public ResultObject() {}

    public ResultObject(String msg, int code, Object data, boolean success, String error) {
        this.msg = msg;
        this.code = code;
        this.data = data;
        this.success = success;
        this.error = error;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
