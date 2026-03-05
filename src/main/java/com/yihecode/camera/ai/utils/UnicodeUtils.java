package com.yihecode.camera.ai.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/**
 * unicode字符处理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public class UnicodeUtils {

    /**
     * unicode转中文
     * 支持 &#x, &#, '\\u' 开头的3种格式
     * @param unicode
     */
    public static String unicode2chinese(String unicode) {
        StringBuffer string = new StringBuffer();
        if (unicode.startsWith("&#x")) {
            String[] hex = unicode.replace("&#x", "").split(";");
            for (int i=0; i<hex.length; i++) {
                int data = Integer.parseInt(hex[i], 16);
                string.append((char) data);
            }
        } else if (unicode.startsWith("&#")) {
            String[] hex = unicode.replace("&#", "").split(";");
            for (int i=0; i<hex.length; i++) {
                int data = Integer.parseInt(hex[i], 10);
                string.append((char) data);
            }
        } else if(unicode.startsWith("\\u")) {
            int start = 0;
            int end = 0;
            while (start > -1) {
                end = unicode.indexOf("\\u", start + 2);
                String charStr = "";
                if (end == -1) {
                    charStr = unicode.substring(start + 2);
                } else {
                    charStr = unicode.substring(start + 2, end);
                }
                char letter = (char) Integer.parseInt(charStr, 16); // 16进制parse整形字符串。
                string.append(new Character(letter).toString());
                start = end;
            }
        } else {
            string.append(unicode);
        }

        return string.toString();
    }


    public static void main(String[] args) throws Exception {
        File file = new File("D:\\_work\\tools\\XWORK\\camera-ai-server\\doc\\中文转韩语_框架整理.txt");


        InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(file));
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        String text = null;
        while((text = bufferedReader.readLine()) != null){
            if(text == null || "".equals(text.trim())) {
                System.out.println();
                continue;
            }
            String ch = UnicodeUtils.unicode2chinese(text.trim());
            System.out.println(text + " " + ch);
        }
    }
}
