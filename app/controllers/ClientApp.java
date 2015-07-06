package controllers;



import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Scanner;


/**
 * Created by jonathan on 2015-07-06.
 */
public class ClientApp {
    public static void main(String[] args) {
        try {
            WebSocketClient s = new WebSocketClient(new URI("ws://play-1-test.herokuapp.com:80/")) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    System.out.println("onopen");
                }

                @Override
                public void onMessage(String message) {
                    System.out.println("msg: " + message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("close: " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    System.out.println("error: " + ex);
                    ex.printStackTrace();
                }
            };
            s.connectBlocking();
            s.send("Hello");
            s.send("Astalavista baby");

        }  catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
