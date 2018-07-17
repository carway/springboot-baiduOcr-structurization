package com.zjw.ocr.api;

import com.alibaba.fastjson.JSONObject;
import com.zjw.ocr.service.OcrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * name
 *
 * @author carway
 * @date 2018/7/17.
 */
@RestController
@RequestMapping("/api/ocr")
public class OcrApi {

    @Autowired
    private OcrService ocrService;

    /**
     * 完整路径：http://localhost:80/api/ocr/handle
     * 表单上传 form-datd
     * @param ocr 文件域 ocr
     * @return Json返回体
     */
    @PostMapping("/handle")
    public Object getOcrStructuralization(@RequestParam("ocr")MultipartFile ocr){
        JSONObject jsonObject = new JSONObject();
        byte[] file =null;
        try {
            file = ocr.getBytes();
        } catch (IOException e) {
            e.printStackTrace();
            jsonObject.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            jsonObject.put("result",false);
            jsonObject.put("message","上传图片失败");
        }
        try{
            jsonObject=ocrService.dataHandle(file);
        }catch (Exception e){
            jsonObject.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            jsonObject.put("result",false);
            jsonObject.put("message","图片识别出错");
        }
        return jsonObject;
    }
}
