package com.zjw.ocr.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 分段的电费
 * @author carway
 * @date 2018/6/9.
 */
@Data
public class SegmentFee implements Serializable {
    private static final long serialVersionUID = 1L;
    private String 电费分段标志;
    private int 扣减电量;
    private int 变损电量;
    private int 线损电量;
    private int 楼道灯电量;
    private int 结算电量;
    private double 电度电价;
    private double 电度电费;
}
