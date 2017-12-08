package com.lqm.mitimerview;

import java.text.SimpleDateFormat;

/**
 * Created by luqinmao on 2017/12/8.
 */

public class Utils {


    public static String getTimeString(int size){

        long  ms = size * 1000 ;//毫秒数

        SimpleDateFormat formatter = new SimpleDateFormat("mm:ss");//初始化Formatter的转换格式。

        String msString = formatter.format(ms);

        return msString;
    }
}
