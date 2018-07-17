package com.zjw.ocr.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baidu.aip.ocr.AipOcr;
import com.zjw.ocr.model.ElectricMeter;
import com.zjw.ocr.model.SegmentFee;
import com.zjw.ocr.service.OcrService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OcrService实现类
 * @author carway
 * @date 2018/7/17.
 */
@Slf4j
@Service()
public class OcrServiceImpl implements OcrService{

    @Value("${baidu.ocr.appId}")
    private String APP_ID;
    @Value("${baidu.ocr.apiKey}")
    private String API_KEY;
    @Value("${baidu.ocr.secretKey}")
    private String SECRET_KEY;


    private Pattern hAsDigitPattern=Pattern.compile(".*\\d+.*");
    private Pattern isDigitPattern = Pattern.compile("[0-9]{1,}");
    private Pattern isDigitPrePattern = Pattern.compile("^(\\d+)(.*)");
    private Pattern getStringOfNumbersPattern = Pattern.compile("\\d+");
    private Pattern splitNotNumberPattern = Pattern.compile("\\D+");
    private Pattern floatsNumberPattern = Pattern.compile("[\\d.]{1,}");
    private Pattern trimNumberPattern = Pattern.compile("\\s*|\\t|\\r|\\n");



    @Override
    public JSONObject dataHandle(byte[] bytes) throws Exception {
        List<String> list = ocr(bytes);
        return structuralization(list);
    }

    /**
     * 百度OCR化（税单类型)
     * @param bytes
     * @return
     */
    private List<String> ocr(byte[] bytes){
        // 初始化一个AipOcr
        AipOcr client = new AipOcr(APP_ID, API_KEY, SECRET_KEY);

        // 可选：设置网络连接参数
        client.setConnectionTimeoutInMillis(2000);
        client.setSocketTimeoutInMillis(60000);

        HashMap<String, String> options = new HashMap<String, String>();
        options.put("recognize_granularity", "big");
        options.put("probability", "true");
        options.put("accuracy", "normal");
        options.put("detect_direction", "true");

        org.json.JSONObject res = client.receipt(bytes, options);

        JSONArray jsonArray = res.getJSONArray("words_result");
        //System.out.println(jsonArray);
        int length =jsonArray.length();
        List<String> list =  new ArrayList<>(length);
        System.out.println(length);
        for (int i=0;i<length;i++){
            org.json.JSONObject result = (org.json.JSONObject)jsonArray.get(i);
            list.add(result.getString("words"));
        }
        return list;
    }


    /**
     * 电费缴费单Ocr化
     * 结构化的时候需求没定义好字段的英文名字，所以直接用中文字段更加直观
     * @param list Ocr化字符串list
     * @return JSONObject（fastJson)
     * @throws Exception
     */
    private JSONObject structuralization(List<String> list)throws Exception{
        com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();
        int i=0;
        String str =list.get(i);
        String value="";
        String next="";
        int range = 0;
        String [] temp;
        if(str.length()>3){
            temp = str.split(":");
            if(temp.length==2){
                jsonObject.put(temp[0],temp[1]);
            }
        }else {
            //第一行只有"增值税"三个字
            jsonObject.put(str,list.get(++i));
        }
        i++;
        str =list.get(i);
        while(!str.equals("计量点编号")){
            temp = str.split(":");
            if(temp.length==2){
                jsonObject.put(temp[0],temp[1]);
                i++;
            }else {
                value =list.get(i+1);
                if(value.equals("计量点编号")){
                    str=list.get(++i);
                    break;
                }else{
                    jsonObject.put(str,value);
                    i=i+2;
                }
            }
            str =list.get(i);
        }



        //计量点编号
        value =list.get(++i);
        jsonObject.put(str,value);

        str=list.get(++i);
        next=list.get(++i);
        if(hasDigit(next)){
            jsonObject.put(str,next);
            i=i+2;
        }else{
            jsonObject.put(str,"");
            i++;
        }
        str=list.get(i);
        if(isDigit(str)){
            //如果是数字
            jsonObject.put("父计量点编号",str);
            i++;
            str=list.get(i);
        }else if(str.equals("电价类别")){
            jsonObject.put("父计量点编号","");
        }else {
            jsonObject.put("父计量点编号","");
            i++;//跳过非法情况
            str=list.get(i);
        }
        //下一个读取的str就必然是电价类别
        value=list.get(++i);
        jsonObject.put(str,value);

        str=list.get(++i);
        value=list.get(++i);
        jsonObject.put(str,value);

        str=list.get(++i);
        value=list.get(++i);
        jsonObject.put(str,value);

        str=list.get(++i);
        while(!isDigitPre(str)){
            str=list.get(++i);
        }


        //下个字符串必然是3400699670有功(总)
        int round=0;
        List<ElectricMeter> meterList = new ArrayList<ElectricMeter>();
        ElectricMeter electricMeter = new ElectricMeter();
        while(!"电费分段标志".equals(str)){
            if (!isDigit(str)){
                electricMeter.set电能表号(getStringOfNumbers(str));
                electricMeter.set抄表示数类型(splitNotNumber(str));
                str=list.get(++i);
                round++;
                log.info("round: "+round+","+getStringOfNumbers(str));
            }else{

                int remainder = round%7;
                switch (remainder){
                    case 1:
                        electricMeter.set上次抄表示数(Integer.valueOf(str));
                        round++;
                        str=list.get(++i);

                        break;
                    case 2:
                        electricMeter.set本次抄表示数(Integer.valueOf(str));
                        round++;
                        str=list.get(++i);

                        break;
                    case 3:
                        electricMeter.set冻结抄表示数(Integer.valueOf(str));
                        round++;
                        str=list.get(++i);

                        break;
                    case 4:
                        String next2 =list.get(i+2);
                        String next3 =list.get(i+3);
                        if(!isDigit(next3)&&isDigitPre(next3)||next3.equals("电费分段标志")&&isDigit(next2)){
                            electricMeter.set倍率(Integer.valueOf(str));
                            round++;
                            str=list.get(++i);
                        }else {
                            electricMeter.set倍率(1);
                            round++;
                        }
                        break;
                    case 5:
                        electricMeter.set加减电量(Integer.valueOf(str));
                        round++;
                        str=list.get(++i);
                        break;
                    case 6:
                        electricMeter.set抄见电量(Integer.valueOf(str));
                        ElectricMeter meter = new ElectricMeter();
                        BeanUtils.copyProperties(electricMeter,meter);
                        //浅拷贝，所以后面赋值不会影响前面的
                        meterList.add(meter);
                        round++;
                        str=list.get(++i);
                        break;
                    default:
                        break;
                }
            }
        }

        //System.out.println(meterList.size());
        //System.out.println(meterList);
        jsonObject.put("ElectricMeterList",meterList);

        log.info(str);

        SegmentFee segmentFee = new SegmentFee();
        List<SegmentFee> segmentFeeList = new ArrayList<SegmentFee>();
        while(!"尖峰".equals(str)){
            str=list.get(++i);
        }

        round=0;
        while(!"峰段".equals(str)){
            int remainder = round%8;
            switch(remainder){
                case 0:
                    segmentFee.set电费分段标志(str);
                    int tempIndex=i;
                    String tempString ="";
                    while(!"峰段".equals(tempString)){
                        tempString=list.get(++tempIndex);
                    }
                    tempString=list.get(--tempIndex);
                    if("0".equals(tempString)){
                        segmentFee.set扣减电量(0);
                        segmentFee.set变损电量(0);
                        segmentFee.set线损电量(0);
                        segmentFee.set楼道灯电量(0);
                        segmentFee.set结算电量(0);
                        tempString=list.get(--tempIndex);
                        segmentFee.set电度电价(Double.valueOf(tempString));
                        round++;
                        i=tempIndex+2;
                        str=list.get(i);
                    }else{
                        round++;
                        str=list.get(++i);
                    }


                    break;
                case 1:
                    segmentFee.set扣减电量(Integer.valueOf(str));
                    round++;
                    str=list.get(++i);
                    break;
                case 2:
                    segmentFee.set变损电量(Integer.valueOf(str));
                    round++;
                    str=list.get(++i);
                    break;
                case 3:
                    segmentFee.set线损电量(Integer.valueOf(str));
                    round++;
                    str=list.get(++i);
                    break;
                case 4:
                    try{
                        segmentFee.set楼道灯电量(Integer.valueOf(str));
                    }catch (NumberFormatException e){
                        segmentFee.set电度电价(Double.valueOf(str));
                    }
                    if(!isDigit(list.get(i+1))){
                        try{
                            Double.valueOf(list.get(i+1));
                        }catch (NumberFormatException e){
                            segmentFee.set电度电费(Integer.valueOf(list.get(i+1)));
                        }
                    }
                    round++;
                    str=list.get(++i);
                    break;
                case 5:
                    try{
                        segmentFee.set结算电量(Integer.valueOf(str));
                    }catch (NumberFormatException e){
                        segmentFee.set电度电价(Double.valueOf(str));
                    }
                    if(!isDigit(list.get(i+1))) {
                        try{
                            Double.valueOf(list.get(i+1));
                        }catch (NumberFormatException e){
                            segmentFee.set电度电费(0);
                        }
                    }
                    round++;
                    str=list.get(++i);
                    break;
                case 6:
                    try{
                        segmentFee.set电度电价(Double.valueOf(str));
                    }catch (NumberFormatException e){
                        segmentFee.set电度电价(0);
                    }
                    if(!isDigit(list.get(i+1))) {
                        try{
                            Double.valueOf(list.get(i+1));
                        }catch (NumberFormatException e){
                            segmentFee.set电度电费(0);
                        }
                    }
                    round++;
                    str=list.get(++i);
                    break;
                case 7:
                    try{
                        segmentFee.set电度电费(Double.valueOf(str));
                    }catch (NumberFormatException e){
                        segmentFee.set电度电费(0);
                    }
                    round++;
                    str=list.get(++i);
                    break;
                default:
                    break;
            }
        }

        SegmentFee spike = new SegmentFee();
        BeanUtils.copyProperties(segmentFee,spike);
        segmentFeeList.add(spike);

        SegmentFee high = new SegmentFee();
        SegmentFee low = new SegmentFee();
        SegmentFee flat = new SegmentFee();
        SegmentFee sum = new SegmentFee();



        int tIndex=i;
        String tempStr=str;
        String electricBill ="";
        String electricPrice ="";
        double eBill=0;
        double ePrice=0;
        while(!"谷段".equals(tempStr)){
            tempStr=list.get(++tIndex);
        }
        //谷段游标
        i=tIndex;
        electricBill =list.get(--tIndex);
        try {
            eBill=Double.valueOf(electricBill);
        }catch (NumberFormatException e){
            String a[]=electricBill.split("\\D");
            if(a.length==3){
                electricPrice=a[0]+"."+a[1].substring(0,5);
                electricBill=a[1].substring(5)+"."+a[2];
            }else{
                high.set电度电价(0);
                high.set电度电费(0);
                throw new RuntimeException("浮点数识别出错1");
            }
        }
        if("".equals(electricPrice)){
            electricPrice =list.get(--tIndex);
        }else {
            try {
                eBill = Double.valueOf(electricBill);
            }catch (NumberFormatException e){
                throw new RuntimeException("");
            }
        }
        try {
            ePrice = Double.valueOf(electricPrice);
        }catch (NumberFormatException e){
            throw new RuntimeException("浮点数识别出错2");
        }
        high.set电度电费(eBill);
        high.set电度电价(ePrice);
        high.set电费分段标志("峰段");
        round=0;
        tempStr=list.get(--tIndex);
        while(!"峰段".equals(tempStr)){
            int remainder = round%5;
            switch (remainder){
                case 0:
                    if("0".equals(tempStr)){
                        high.set结算电量((int)Math.ceil(high.get电度电费()/high.get电度电价()));
                    }else{
                        high.set结算电量(Integer.valueOf(tempStr));
                        tempStr=list.get(--tIndex);
                    }
                    round++;
                    break;
                case 1:
                    high.set楼道灯电量(Integer.valueOf(tempStr));
                    if(!isDigit(list.get(tIndex-1))) {
                        high.set线损电量(0);
                        high.set变损电量(0);
                        high.set扣减电量(0);
                    }
                    round++;
                    tempStr=list.get(--tIndex);
                    break;
                case 2:
                    high.set线损电量(Integer.valueOf(tempStr));
                    if(!isDigit(list.get(tIndex-1))) {
                        high.set变损电量(0);
                        high.set扣减电量(0);
                    }
                    round++;
                    tempStr=list.get(--tIndex);
                    break;
                case 3:
                    high.set变损电量(Integer.valueOf(tempStr));
                    if(!isDigit(list.get(tIndex-1))) {
                        high.set变损电量(0);
                    }
                    round++;
                    tempStr=list.get(--tIndex);
                    break;
                case 4:
                    high.set扣减电量(Integer.valueOf(tempStr));
                    round++;
                    tempStr=list.get(--tIndex);
                    break;
                default:
                    break;
            }
        }



        //当前tIndex为谷段游标
        tIndex=i;
        tempStr=str=list.get(i);
        electricBill ="";
        electricPrice ="";
        eBill=0;
        ePrice=0;
        while(!"平段".equals(tempStr)){
            tempStr=list.get(++tIndex);
        }
        //当前i为平段游标
        i=tIndex;
        electricBill =list.get(--tIndex);
        try {
            eBill=Double.valueOf(electricBill);
        }catch (NumberFormatException e){

            String a[]=electricBill.split("\\D");
            System.out.println(a.length);
            for (int j = 0; j < a.length; j++) {
                System.out.println(a[j]);
            }
            if(a.length==3){
                electricPrice=a[0]+"."+a[1].substring(0,5);
                electricBill=a[1].substring(5)+"."+a[2];
            }else{
                low.set电度电价(0);
                low.set电度电费(0);
                throw new RuntimeException("浮点数识别出错21");
            }
        }
        if("".equals(electricPrice)){
            electricPrice =list.get(--tIndex);
        }else {
            try {
                eBill = Double.valueOf(electricBill);
            }catch (NumberFormatException e){
                throw new RuntimeException("");
            }
        }
        try {
            ePrice = Double.valueOf(electricPrice);
        }catch (NumberFormatException e){
            throw new RuntimeException("浮点数识别出错22");
        }
        low.set电度电费(eBill);
        low.set电度电价(ePrice);
        low.set电费分段标志("谷段");


        round=0;
        tempStr=list.get(--tIndex);
        while(!"谷段".equals(tempStr)){
            int remainder = round%5;
            switch (remainder){
                case 0:
                    if("0".equals(tempStr)){
                        low.set结算电量((int)Math.ceil(low.get电度电费()/low.get电度电价()));
                    }else{
                        low.set结算电量(Integer.valueOf(tempStr));
                        tempStr=list.get(--tIndex);
                    }
                    round++;
                    break;
                case 1:
                    low.set楼道灯电量(Integer.valueOf(tempStr));
                    if(!isDigit(list.get(tIndex-1))) {
                        low.set线损电量(0);
                        low.set变损电量(0);
                        low.set扣减电量(0);
                    }
                    round++;
                    tempStr=list.get(--tIndex);
                    break;
                case 2:
                    low.set线损电量(Integer.valueOf(tempStr));
                    if(!isDigit(list.get(tIndex-1))) {
                        low.set变损电量(0);
                        low.set扣减电量(0);
                    }
                    round++;
                    tempStr=list.get(--tIndex);
                    break;
                case 3:
                    low.set变损电量(Integer.valueOf(tempStr));
                    if(!isDigit(list.get(tIndex-1))) {
                        low.set变损电量(0);
                    }
                    round++;
                    tempStr=list.get(--tIndex);
                    break;
                case 4:
                    low.set扣减电量(Integer.valueOf(tempStr));
                    round++;
                    tempStr=list.get(--tIndex);
                    break;
                default:
                    break;
            }
        }

        //当前tIndex为平段游标
        tIndex=i;
        tempStr=str=list.get(i);
        electricBill ="";
        electricPrice ="";
        eBill=0;
        ePrice=0;
        while(!"小计".equals(tempStr)){
            tempStr=list.get(++tIndex);
        }
        //当前i为小计游标
        i=tIndex;
        electricBill =list.get(--tIndex);
        try {
            eBill=Double.valueOf(electricBill);
        }catch (NumberFormatException e){

            String a[]=electricBill.split("\\D");
            System.out.println(a.length);
            for (int j = 0; j < a.length; j++) {
                System.out.println(a[j]);
            }
            if(a.length==3){
                electricPrice=a[0]+"."+a[1].substring(0,5);
                electricBill=a[1].substring(5)+"."+a[2];
            }else{
                flat.set电度电价(0);
                flat.set电度电费(0);
                throw new RuntimeException("浮点数识别出错31");
            }
        }
        if("".equals(electricPrice)){
            electricPrice =list.get(--tIndex);
        }else {
            try {
                eBill = Double.valueOf(electricBill);
            }catch (NumberFormatException e){
                throw new RuntimeException("");
            }
        }
        try {
            ePrice = Double.valueOf(electricPrice);
        }catch (NumberFormatException e){
            throw new RuntimeException("浮点数识别出错22");
        }
        flat.set电度电费(eBill);
        flat.set电度电价(ePrice);
        flat.set电费分段标志("平段");

        round=0;
        tempStr=list.get(--tIndex);
        while(!"平段".equals(tempStr)){
            int remainder = round%5;
            switch (remainder){
                case 0:
                    if("0".equals(tempStr)){
                        flat.set结算电量((int)Math.ceil(flat.get电度电费()/flat.get电度电价()));
                    }else{
                        flat.set结算电量(Integer.valueOf(tempStr));
                        tempStr=list.get(--tIndex);
                    }
                    round++;
                    break;
                case 1:
                    flat.set楼道灯电量(Integer.valueOf(tempStr));
                    if(!isDigit(list.get(tIndex-1))) {
                        flat.set线损电量(0);
                        flat.set变损电量(0);
                        flat.set扣减电量(0);
                    }
                    round++;
                    tempStr=list.get(--tIndex);
                    break;
                case 2:
                    flat.set线损电量(Integer.valueOf(tempStr));
                    if(!isDigit(list.get(tIndex-1))) {
                        flat.set变损电量(0);
                        flat.set扣减电量(0);
                    }
                    round++;
                    tempStr=list.get(--tIndex);
                    break;
                case 3:
                    flat.set变损电量(Integer.valueOf(tempStr));
                    if(!isDigit(list.get(tIndex-1))) {
                        flat.set变损电量(0);
                    }
                    round++;
                    tempStr=list.get(--tIndex);
                    break;
                case 4:
                    flat.set扣减电量(Integer.valueOf(tempStr));
                    round++;
                    tempStr=list.get(--tIndex);
                    break;
                default:
                    break;
            }
        }

        sum.set电费分段标志("小计");
        sum.set变损电量(spike.get变损电量()+high.get变损电量()+low.get变损电量()+flat.get变损电量());
        sum.set线损电量(spike.get线损电量()+high.get线损电量()+low.get线损电量()+flat.get线损电量());
        sum.set楼道灯电量(spike.get楼道灯电量()+high.get楼道灯电量()+low.get楼道灯电量()+flat.get楼道灯电量());
        str=list.get(++i);
        while(isDigitPre(str)){
            str=list.get(++i);
        }
        try{
            sum.set结算电量(Integer.valueOf(list.get(i-2)));
            sum.set电度电费(Double.valueOf(list.get(i-1)));
        }catch (NumberFormatException e){
            throw new RuntimeException("ocr识别数字出错4");
        }


        segmentFeeList.add(high);
        segmentFeeList.add(low);
        segmentFeeList.add(flat);
        segmentFeeList.add(sum);
        jsonObject.put("segmentFeeList",segmentFeeList);

        str=str.substring(20);
        List<String> extraList=getFloatsNumber(str);
        if(extraList.size()==2){
            try{
                jsonObject.put("代征各项基金和附加费",Double.valueOf(extraList.get(0)));
                jsonObject.put("单价合计(元/千瓦时)",Double.valueOf(extraList.get(1)));
            }catch (NumberFormatException e){
                throw  new RuntimeException("浮点数转换失败5");
            }
        }
        str=list.get(++i);
        temp = str.split(":");
        if(temp.length==2){
            jsonObject.put(temp[0],temp[1]);
        }
        str=list.get(++i);
        temp = str.split(":");
        if(temp.length==2){
            jsonObject.put(temp[0],temp[1]);
        }else{
            jsonObject.put(str.substring(0,6),"");
        }
        str=list.get(++i);
        temp = str.split(":");
        if(temp.length==2){
            jsonObject.put(temp[0],temp[1]);
        }

        str=list.get(++i);
        jsonObject.put(str.substring(0,2),str.substring(2,13));
        str=str.substring(14);
        List<String> needFeeList=getFloatsNumber(str);
        if(needFeeList.size()==2){
            try{
                jsonObject.put("本月应补交电费",Double.valueOf(needFeeList.get(0)));
                jsonObject.put("预收电费余额",Double.valueOf(needFeeList.get(1)));
            }catch (NumberFormatException e){
                throw  new RuntimeException("浮点数转换失败6");
            }
        }

        for(int j=0;j<7;j++){
            str=list.get(++i);
            temp = str.split(":");
            if(temp.length==2){
                jsonObject.put(temp[0],temp[1]);
            }
        }
        i=i+2;
        str=list.get(i);
        if(isDigit(str)){
            jsonObject.put("电话",str);
        }else{
            jsonObject.put("电话","");
            i--;
        }
        i=i+2;
        str=list.get(i);
        if(str.length()!=47&&hasDigit(str)){
            jsonObject.put("备注",str);
        }
        StringBuffer stringBuffer = new StringBuffer();
        for(i=i+1;i<list.size();i++){
            stringBuffer.append(list.get(i));
        }
        jsonObject.put("remark",replaceBlank(stringBuffer.toString()));

        log.info(jsonObject.toString());
        return jsonObject;
    }



    private boolean hasDigit(String content) {
        boolean flag = false;
        Matcher m = hAsDigitPattern.matcher(content);
        if (m.matches()) {
            flag = true;
        }
        return flag;
    }

    private boolean isDigit(String strNum) {
        Matcher matcher = isDigitPattern.matcher((CharSequence) strNum);
        return matcher.matches();
    }

    private boolean isDigitPre(String strNum) {
        Matcher matcher = isDigitPrePattern.matcher(strNum);
        return matcher.matches();
    }

    private String getStringOfNumbers(String content) {
        Matcher matcher = getStringOfNumbersPattern.matcher(content);
        while (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    private String splitNotNumber(String content) {
        Matcher matcher = splitNotNumberPattern.matcher(content);
        while (matcher.find()) {
            return matcher.group(0);
        }
        return "";
    }

    private List<String> getFloatsNumber(String content){
        Matcher matcher = floatsNumberPattern.matcher(content);
        List<String> list = new ArrayList<String>();
        int i=0;
        while(matcher.find()){
            list.add(matcher.group(i));
        }
        return list;
    }

    private String replaceBlank(String str) {
        String dest = "";
        if (str!=null) {
            Matcher m = trimNumberPattern.matcher(str);
            dest = m.replaceAll("");
        }
        return dest;
    }
}
