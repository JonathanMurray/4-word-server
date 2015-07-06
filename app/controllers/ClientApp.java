package controllers;



import models.Person;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.FrameBuilder;
import org.java_websocket.handshake.ServerHandshake;

import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
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
                public void onMessage(ByteBuffer bytes){
                    try {
                        Person p = (Person) new ObjectInputStream(new ByteArrayInputStream(bytes.array())).readObject();
                        System.out.println("Received from server: " + p);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("close: " + reason + "code: " +  code);
                }

                @Override
                public void onError(Exception ex) {
                    System.out.println("error: " + ex);
                    ex.printStackTrace();
                }
            };
            s.connectBlocking();


            Person p = new Person("Johny", 18);
            System.out.println("Created person: " + p);
            ByteArrayOutputStream out =new ByteArrayOutputStream();
            new ObjectOutputStream(out).writeObject(p);
            System.out.println("Sending it to server");
            s.send(out.toByteArray());

            p = new Person("Mike", 50);
            System.out.println("Created person: " + p);
            out =new ByteArrayOutputStream();
            new ObjectOutputStream(out).writeObject(p);
            System.out.println("Sending it to server");
            s.send(out.toByteArray());




        }  catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
