package controllers;

import fourword_shared.messages.ClientMsg;
import fourword_shared.messages.Msg;
import play.Logger;
import play.mvc.Http;
import play.mvc.Http.WebSocketEvent;
import play.mvc.WebSocketController;

import java.io.*;


public class ServerController extends WebSocketController {




    public static void connect() {
        System.out.println("A client has connected");

        while(inbound.isOpen()){
            System.out.println("Waiting for next inbound event...");
            WebSocketEvent e = await(inbound.nextEvent());
            System.out.println("Received websocketevent " + e);
            if(e instanceof Http.WebSocketFrame) {
                Http.WebSocketFrame frame = (Http.WebSocketFrame)e;
                try {
                    Msg<ClientMsg> msg = objectFromBytes(frame.binaryData);
                    handleMessage(msg);
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

    private  static <T> T objectFromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        return (T) new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject();
    }

    private static byte[] bytesFromObject(Object obj) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ObjectOutputStream(out).writeObject(obj);
        return out.toByteArray();
    }



    private static void handleMessage(Msg<ClientMsg> msg){
        try {

            System.out.println("Received msg: " + msg);
            System.out.println("Sending it back");
            byte opcode = 0x2; //binary
            outbound.send(opcode, bytesFromObject(msg));
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }
}