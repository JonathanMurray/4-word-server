package controllers;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jonathan on 2015-07-06.
 */
public class Server {
    public static final Server INSTANCE = new Server();

    private Server(){

    }

//    private ServerSocket serverSocket;
    private List<String> playerNames = new ArrayList<String>();
//    private HashMap<String, PlayerSocket> nameSocketMap = new HashMap<String, PlayerSocket>();
//    private HashMap<String, Lobby> hostLobbyMap = new HashMap<String, Lobby>();
//    private HashMap<String, GameObject> hostGameMap = new HashMap<>();
}
