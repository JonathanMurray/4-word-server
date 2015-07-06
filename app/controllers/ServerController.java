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



    public static void connect() {
        System.out.println("A client has connected");

        while(inbound.isOpen()){
            System.out.println("Waiting for next inbound event...");
            await(inbound.nextEvent(), new MessageHandler());
        }
    }

    private static class MessageHandler implements F.Action<WebSocketEvent> {

        @Override
        public void invoke(WebSocketEvent e) {
            System.out.println("Received websocketevent " + e);
            if(e instanceof Http.WebSocketFrame) {
                Http.WebSocketFrame frame = (Http.WebSocketFrame)e;
                try {
                    Person p = (Person) new ObjectInputStream(new ByteArrayInputStream(frame.binaryData)).readObject();
                    System.out.println("Received person: " + p);

                    ByteArrayOutputStream out =new ByteArrayOutputStream();
                    new ObjectOutputStream(out).writeObject(p);
                    System.out.println("Sending it back");
                    byte opcode = 0x2; //binary
                    outbound.send(opcode, out.toByteArray());
                    out.flush();
                    out.close();


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