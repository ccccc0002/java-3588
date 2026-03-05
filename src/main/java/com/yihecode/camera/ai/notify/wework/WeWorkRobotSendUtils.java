package com.yihecode.camera.ai.notify.wework;

import cn.hutool.core.date.DateUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 描述：企业微信机器人发送消息
 * https://open.work.weixin.qq.com/help2/pc/18401
 * https://developer.work.weixin.qq.com/document/path/91770?version=4.1.0.70174&platform=mac
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Slf4j
public class WeWorkRobotSendUtils {

    /**
     * 发送文本消息
     * @param content
     */
    public static void sendText(String reqUrl, String content) {
        //
        Map<String, Object> contentMap = new HashMap<>();
        contentMap.put("content", content);

        //
        Map<String, Object> params = new HashMap<>();
        params.put("msgtype", "text");
        params.put("text", contentMap);

        try {
            String response = HttpUtil.post(reqUrl, JSON.toJSONString(params));
            log.info("群机器人发送文本消息", response);
        } catch (Exception e) {
            log.error("群机器人发送文本消息异常", e);
        }

    }

    /**
     * 发送图文消息
     * @param title
     * @param url
     * @param picurl
     */
    public static void sendTextAndImage(String reqUrl, String title, String url, String picurl) {
        List<Map<String, Object>> articles = new ArrayList<>();

        //
        Map<String, Object> article = new HashMap<>();
        article.put("title", title);
        article.put("url", url);
        article.put("picurl", picurl);
        articles.add(article);

        //
        Map<String, Object> news = new HashMap<>();
        news.put("articles", articles);

        //
        Map<String, Object> params = new HashMap<>();
        params.put("msgtype", "news");
        params.put("news", news);

        //
        try {
            String response = HttpUtil.post(reqUrl, JSON.toJSONString(params));
            log.info("群机器人发送图文消息", response);
        } catch (Exception e) {
            //
            log.info("群机器人发送图片消息异常", e);
        }
    }
}
