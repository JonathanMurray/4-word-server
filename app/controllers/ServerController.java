package controllers;

import models.Person;
import org.java_websocket.util.Base64;
import play.Logger;
import play.libs.F;
import play.mvc.Http;
import play.mvc.Http.WebSocketEvent;
import play.mvc.WebSocketController;

import java.io.*;


public class ServerController extends WebSocketController {

    public ServerController(){
        System.out.println("ServerController constructor");
    }

    public static void connect() {
        System.out.println("A client has connected");

        while(inbound.isOpen()){
            await(inbound.nextEvent(), new MessageHandler());
        }
    }

    private static class MessageHandler implements F.Action<WebSocketEvent> {

        @Override
        public void invoke(WebSocketEvent e) {
            if(e instanceof Http.WebSocketFrame) {
                Http.WebSocketFrame frame = (Http.WebSocketFrame)e;
                try {
                    Person p = (Person) new ObjectInputStream(new ByteArrayInputStream(frame.binaryData)).readObject();
                    System.out.println("Received person: " + p);
                    ByteArrayOutputStream out =new ByteArrayOutputStream();
                    new ObjectOutputStream(out).writeObject(p);
                    System.out.println("Sending it back");
                    outbound.send((byte)0, out.toByteArray());
                } catch (IOException e1) {
                    e1.printStackTrace();
                } catch (ClassNotFoundException e1) {
                    e1.printStackTrace();
                }
            }
            if(e instanceof Http.WebSocketClose) {
                Logger.info("Socket closed!");
            }
        }

    }

}