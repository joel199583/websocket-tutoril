package com.websocket.app.service;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.websocket.app.bean.ChatBean;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
@ServerEndpoint(value = "/chatroom/{name}")
public class ChatroomServer {


    //静态变量，用来记录当前在线连接数。应该把它设计成线程安全的。
    private static AtomicInteger online = new AtomicInteger();

    //concurrent包的執行續安全Map，用来存放每個客户端對應的連線。websocket container
    private static Map<String, Session> sessionPools = new ConcurrentHashMap<>();
    
    /**
     * 連線建立成功呼叫的方法
     * @param session 客戶端與伺服器端建立的連線
     * @param userName 客户端的userName (識別碼)
     */
    @OnOpen
    public void onOpen(Session session, @PathParam(value = "name") String userName) throws Exception{
        sessionPools.put(userName, session);
        addOnlineCount();
        System.out.println(userName + "加入webSocket！當前人數為" + online);
		sessionPools.forEach((k, otherSession) -> {
			
	
			ChatBean bean = new ChatBean();
			bean.setUsers(sessionPools.keySet()
									  .stream()
									  .filter(s -> !s.equals(k))
									  .collect(Collectors.toSet()));
			bean.setType("open");
			bean.setMsg("歡迎 " + userName + " 加入聊天！");
			sendMessage(otherSession, bean);	
		});
    }

    /**
     * 連線關閉呼叫的方法
     * @param userName 關閉連線的userName
     */
    @OnClose
    public void onClose(@PathParam(value = "name") String userName){
        sessionPools.remove(userName);
        subOnlineCount();
        System.out.println(userName + "關閉webSocket連線！當前人数為" + online);
        sessionPools.forEach((k, session) -> {
			ChatBean bean = new ChatBean();
			bean.setUsers(sessionPools.keySet()
									  .stream()
									  .filter(s -> !s.equals(k))
									  .collect(Collectors.toSet()));
			bean.setType("close");
			bean.setMsg(userName + " 離開聊天！");
			sendMessage(session, bean);
		});
    }

    /**
     * 收到客户端消息時觸發
     * @param message
     * @throws IOException
     */
    @OnMessage
    public void onMessage(@PathParam(value = "name") String fromUser, String jsonString) throws IOException{
    	ChatBean chatContent = new ObjectMapper().readValue(jsonString, ChatBean.class);
    	
    	if("all".equals(chatContent.getTo())) {
    		sessionPools.forEach((key, session) -> {
    			ChatBean rtnBean = new ChatBean();
    			rtnBean.setType("sendMsg");
    			rtnBean.setMsg("(全體)" + fromUser + " : " + chatContent.getMsg());
    			sendMessage(session, rtnBean);
            });
    	} else {
    		ChatBean rtnBean = new ChatBean();
			rtnBean.setType("sendMsg");
			rtnBean.setMsg("(悄悄話)" + fromUser + " : " + chatContent.getMsg());
			sendMessage(sessionPools.get(chatContent.getTo()), rtnBean);
    	}
    }

    /**
     * 發生異常時觸發
     * @param session
     * @param throwable
     */
    @OnError
    public void onError(Session session, Throwable throwable){
        System.out.println("發生錯誤");
        throwable.printStackTrace();
    }
    
    /**
     * 發送訊息
     * @param session 客戶端與伺服器端建立的連線
     * @param message 訊息
     * @throws IOException
     */
    public void sendMessage(Session session, String message) {
        if(session != null){
        	try {
        		session.getBasicRemote().sendText(message);;
            } catch(IOException e){
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 發送訊息(傳送內容為 自訂義物件 ChatBean)
     * @param session 客戶端與伺服器端建立的連線
     * @param message 訊息
     * @throws IOException
     */
    public void sendMessage(Session session, ChatBean chatBean) {
    	if(session != null){
        	try {
        		session.getBasicRemote().sendText(new ObjectMapper().writeValueAsString(chatBean));;
            } catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    /**
     * 给指定用户發送消息
     * @param userName 用户名
     * @param message 消息
     * @throws IOException
     */
    public void sendInfo(String fromUser, String userName, String message){
        Session session = sessionPools.get(userName);
        try {
            sendMessage(session, "來自 " + userName + " 的訊息:" + message);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void addOnlineCount(){
        online.incrementAndGet();
    }

    public static void subOnlineCount() {
        online.decrementAndGet();
    }
}