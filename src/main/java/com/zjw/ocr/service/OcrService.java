package com.zjw.ocr.service;


import com.alibaba.fastjson.JSONObject;

/**
 * OcrService接口
 * @author carway
 * @date 2018/7/17.
 */
public interface OcrService {

    /**
     * 数据处理（Ocr化和数据结构化）
     * @param bytes 字节数组
     * @return 格式化后的json数据
     * @throws Exception
     */
    public JSONObject dataHandle(byte[] bytes) throws Exception;

}
