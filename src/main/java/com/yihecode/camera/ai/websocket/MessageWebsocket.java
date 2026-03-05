package com.yihecode.camera.ai.websocket;

import cn.hutool.json.JSONUtil;
import com.yihecode.camera.ai.enums.MessageType;
import com.yihecode.camera.ai.vo.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 首页弹窗消息socket
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Slf4j
@Component
@ServerEndpoint(value = "/message/{uid}")
public class MessageWebsocket {

    //
    private static final AtomicInteger onlineCount = new AtomicInteger(0);

    //
    private static final ConcurrentHashMap<String, Session> sessionMap = new ConcurrentHashMap<>();

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam(value = "uid") String uid) {
        //
        sessionMap.put(uid, session);

        //
        onlineCount.incrementAndGet();
        //log.info("有新连接加入：{}，当前在线人数为：{}", session.getId(), onlineCount.get());
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(Session session, @PathParam(value = "uid") String uid) {
        //
        sessionMap.remove(uid);

        //
        onlineCount.decrementAndGet(); // 在线数减1

        //
        try {
            session.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message
     *            客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        //
        if(session == null) {
            System.out.println("OnMessage session is null");
            return ;
        }

        //
        if(!session.isOpen()) {
            System.out.println("OnMessage session is close");
            return;
        }

        //
        final RemoteEndpoint.Basic basic = session.getBasicRemote();
        if(basic == null) {
            System.out.println("OnMessage basic is null");
            return;
        }

        try {
            Message messageVo = new Message();
            messageVo.setType(MessageType.HEART.getType());
            messageVo.setContent(System.currentTimeMillis() + "");
            basic.sendText(JSONUtil.toJsonStr(messageVo));
        } catch (Exception e) {
            System.out.println("OnMessage Error: " + e.getMessage());
        }
    }

    /**
     *
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        //
        try {
            session.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //
    public void sendToAll(String message) {
        sessionMap.forEach((sessionId, session) -> sendToUser(session, message));
    }

    /**
     * send message to all
     * @param session
     * @param message
     */
    private synchronized void sendToUser(Session session, String message) {
        //
        if(session == null) {
            return ;
        }

        //
        final RemoteEndpoint.Basic basic = session.getBasicRemote();
        if(basic == null) {
            return;
        }

        //
        try {
            basic.sendText(message);
        } catch (IOException e) {
            log.error("Websocket sendMessage IOException {}" + e.getMessage());
        }
    }
}