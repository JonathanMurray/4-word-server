package controllers;

import fourword_shared.messages.*;
import fourword_shared.model.Lobby;
import fourword_shared.model.LobbyPlayer;
import play.Logger;
import play.mvc.Http;
import play.mvc.Http.WebSocketEvent;
import play.mvc.Scope;
import play.mvc.WebSocketController;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class ServerController extends WebSocketController {

    private static boolean isLoggedIn;
    private static Server server = Server.INSTANCE;
    private static String name;


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
//                    handleClientMessage(msg);
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

    private static void printRequest(Http.Request req){
        System.out.println("---Request:");
        System.out.println("action: " + req.action);
        System.out.println("host: " + req.host);
        System.out.println("domain: " + req.domain);
        System.out.println("remoteaddr: " + req.remoteAddress);
    }

    private static void printSession(Scope.Session s){
        System.out.println("---Session:");
        System.out.println("id: " + s.getId());
        System.out.println("auth-token: " + s.getAuthenticityToken());
    }



    private static void handleMessage(Msg<ClientMsg> msg){
        try {
            System.out.println();
            System.out.println("request: " + request);
            System.out.println("params: " + params);
            System.out.println("validation: " + validation);
            System.out.println("session: " + session);


            printRequest(request);
            printSession(session);
            System.out.println();

            System.out.println("Received msg: " + msg);
            System.out.println("Sending reply...");
            byte opcode = 0x2; //binary
            Server.INSTANCE.TEST_VALUES.add("X" + new Random().nextInt(1000));
            outbound.send(opcode, bytesFromObject(new MsgStringList(ServerMsg.ONLINE_PLAYERS, new ArrayList<String>(Server.INSTANCE.TEST_VALUES))));
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }
    
    private static void sendMessage(Msg<ServerMsg> msg) throws IOException {
        byte opcode = 0x2; //binary
        outbound.send(opcode, bytesFromObject(msg));
    }
    
    private static void setName(String name){
        ServerController.name = name;
    }

//    private static boolean handleClientMessage(Msg<ClientMsg> msg) throws IOException {
//        switch (msg.type()){
//            case LOGIN:
//                if(isLoggedIn){
//                    throw new RuntimeException();
//                }
//                String name = ((MsgText)msg).text;
//                if(server.validPlayerName(name)) {
//
//                    sendMessage(new Msg(ServerMsg.OK));
//                    setName(name);
//                    server.addPlayer(name, thisSocket);
//                    System.out.println(name + " has logged in.");
//                    server.printState();
//                    isLoggedIn = true;
//                }else{
//                    thisSocket.sendMessage(new MsgText(ServerMsg.NO, "Invalid name!"));
//                }
//                break;
//
//            case ACCEPT_INVITE:
//                Lobby otherLobby = server.getLobbyOfHost(thisSocket.getInvitedBy());
//                otherLobby.setConnected(thisSocket.getName());
//                broadcastLobbyState(otherLobby);
//                thisSocket.joinLobby(otherLobby);
//                server.printState();
//                break;
//            case DECLINE_INVITE:
//                otherLobby = server.getLobbyOfHost(thisSocket.getInvitedBy());
//                thisSocket.removeInvite();
//                otherLobby.removePlayer(thisSocket.getName());
//                broadcastLobbyState(otherLobby);
//                server.printState();
//                break;
//
//            case LOGOUT:
//                server.removePlayer(thisSocket.getName());
//                break;
//            case CREATE_LOBBY:
//                Lobby newLobby = new Lobby(thisSocket.getName());
//                thisSocket.joinLobby(newLobby);
//                server.addLobby(thisSocket.getName(), newLobby);
//                server.printState();
//                break;
//            case INVITE_TO_LOBBY:
//                handleInvite((MsgText) msg);
//                break;
//            case ADD_BOT_TO_LOBBY:
//                String botName = server.generateBotName();
//
//                //The bot-socket has no dedicated thread like the human-sockets
//                BotSocket botSocket = new BotSocket(new AI(), botName);
//                server.addPlayer(botName, botSocket);
//                botSocket.joinLobby(thisSocket.getLobby());
//                thisSocket.getLobby().addPlayer(LobbyPlayer.bot(botName));
//                broadcastLobbyState(thisSocket.getLobby());
//                server.printState();
//                break;
//            case KICK_FROM_LOBBY:
//                handleKick((MsgText) msg);
//                break;
//            case LEAVE_LOBBY:
//                leaveLobby(thisSocket);
//                break;
//            case START_GAME_FROM_LOBBY:
//                Lobby lobby = thisSocket.getLobby();
//                boolean enoughPlayers = lobby.size() > 1;
//                if(enoughPlayers){
//                    broadcastInLobby(lobby, new MsgGameIsStarting(lobby.numCols, lobby.numRows, lobby.sortedNames().toArray(new String[0])));
//                    server.createGameHostedBy(thisSocket.getName(), lobby.size(), lobby.numCols, lobby.numRows);
//                    for(LobbyPlayer bot : lobby.getAllBots()){
//                        server.joinGameHostedBy(thisSocket.getName(), bot.name);
//                    }
//                    server.printState();
//                }else{
//                    thisSocket.sendMessage(new MsgText(ServerMsg.NO, "Not enough playerNames!"));
//                }
//
//                break;
//
//            case LOBBY_SET_DIMENSIONS:
//                thisSocket.getLobby().numRows = ((MsgLobbySetDim)msg).numRows;
//                thisSocket.getLobby().numCols = ((MsgLobbySetDim)msg).numCols;
//                broadcastLobbyState(thisSocket.getLobby());
//                break;
//
//            case CONFIRM_GAME_STARTING:
//                String host = ((MsgText)msg).text;
//                boolean newGameStarted = server.joinGameHostedBy(host, thisSocket.getName());
//                if(newGameStarted){
//                    server.removeLobby(host);
//                }
//                thisSocket.leaveLobby();
//                server.printState();
//                return true; //Return from this runnable. It's job is done!
//
//            case QUICK_START_GAME:
//                MsgQuickStartGame quickStart = (MsgQuickStartGame) msg;
//                server.createGameHostedBy(thisSocket.getName(), 1 + quickStart.numBots, quickStart.numCols, quickStart.numRows);
//                List<String> names = new ArrayList<String>();
//                for(int i = 0; i < quickStart.numBots; i++){
//                    botName = server.generateBotName();
//                    botSocket = new BotSocket(new AI(), botName);
//                    names.add(botName);
//                    server.addPlayer(botName, botSocket);
//                    server.joinGameHostedBy(thisSocket.getName(), botName);
//                }
//                names.add(thisSocket.getName());
//                thisSocket.sendMessage(new MsgGameIsStarting(quickStart.numCols, quickStart.numRows, names.toArray(new String[0])));
//                server.printState();
//                return false;
//        }
//        return false; //Thread is not done yet
//    }
//
//    private void handleInvite(MsgText inviteMsg) throws IOException {
//        String invitedName = inviteMsg.text;
//        String inviterName = thisSocket.getName();
//        boolean playerFound, selfInvite;
//        PlayerSocket invitedSocket;
//
//        invitedSocket = server.getSocket(invitedName);
//        playerFound = server.containsPlayer(invitedName) && invitedSocket.isRemote();
//        selfInvite = inviterName.equals(invitedName);
//        if(selfInvite){
//            thisSocket.sendMessage(new MsgText(ServerMsg.NO, "You can't invite yourself!"));
//        }else if(!playerFound){
//            thisSocket.sendMessage(new MsgText(ServerMsg.NO, "Can't find that player!"));
//        }else if(invitedSocket.isInvited()) {
//            thisSocket.sendMessage(new MsgText(ServerMsg.NO, "That player already has a pending invite!"));
//        }else if(invitedSocket.isInLobby()) {
//            thisSocket.sendMessage(new MsgText(ServerMsg.NO, "That player is already in a lobby!"));
//        }else{
//            thisSocket.sendMessage(new Msg(ServerMsg.OK));
//            thisSocket.getLobby().addPlayer(LobbyPlayer.pendingHuman(invitedName));
//            invitedSocket.sendMessage(new MsgText(ServerMsg.YOU_ARE_INVITED, inviterName));
//            broadcastLobbyState(thisSocket.getLobby());
//            invitedSocket.setInvitedBy(inviterName);
//            server.printState();
//        }
//    }
//
//    private void handleKick(MsgText kickMsg) throws IOException {
//        String kickedPlayer = kickMsg.text;
//        PlayerSocket kickedSocket = server.getSocket(kickedPlayer);
//        kickedSocket.sendMessage(new Msg(ServerMsg.YOU_WERE_KICKED));
//        leaveLobby(kickedSocket);
//    }
//
//    private void leaveLobby(PlayerSocket socket) throws IOException {
//        Lobby lobby = socket.getLobby();
//        boolean isHost = socket.isHostOfLobby();
//        socket.leaveLobby();
//        lobby.removePlayer(socket.getName());
//
//        if(isHost){
//            ArrayList<LobbyPlayer> otherHumansInLobby = lobby.getAllHumans();
//            if(otherHumansInLobby.isEmpty()){
//                for(LobbyPlayer bot : lobby.getAllBots()){
//                    server.removePlayer(bot.name);
//                }
//                server.removeLobby(socket.getName());
//            }else{
//                String newHost = otherHumansInLobby.get(0).name;
//                lobby.setNewHost(newHost);
//            }
//        }
//        broadcastLobbyState(lobby);
//        server.printState();
//    }
//
//
//
//    //Not sent to bots
//    public void broadcastLobbyState(Lobby lobby) throws IOException {
//        broadcastInLobby(lobby, new MsgLobbyState(lobby));
//    }
//
//    //Not sent to bots
//    public void broadcastInLobby(Lobby lobby, Msg<ServerMsg> msg) throws IOException {
//        for(String playerInLobby : lobby.sortedNames()){
//            if(lobby.isConnected(playerInLobby) && lobby.isHuman(playerInLobby)){
//                server.getSocket(playerInLobby).sendMessage(msg);
//            }
//        }
//    }


}