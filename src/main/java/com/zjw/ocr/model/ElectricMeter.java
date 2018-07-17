package com.zjw.ocr.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 电能表
 * @author carway
 * @date 2018/6/9.
 */
@Data
public class ElectricMeter implements Serializable {
    private static final long serialVersionUID = 1L;
    private String 电能表号;
    private String 抄表示数类型;
    private int 上次抄表示数;
    private int 本次抄表示数;
    private int 冻结抄表示数;
    private int 倍率;
    private int 加减电量;
    private int 抄见电量;


}
