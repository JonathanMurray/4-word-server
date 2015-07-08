package controllers;

import fourword_shared.messages.*;
import play.Logger;
import play.libs.F;
import play.libs.F.*;
import play.mvc.Http;
import play.mvc.Http.WebSocketEvent;
import play.mvc.Scope;
import play.mvc.WebSocketController;
import play.mvc.WebSocketInvoker;
import play.mvc.results.WebSocketResult;

import java.io.*;
import java.util.ArrayList;
import java.util.Random;


public class ServerController extends WebSocketController {

    private static boolean isLoggedIn;
    private static Server server = Server.INSTANCE;
    private static String name;


    public static void connect() {
        String s = "[" + request.remoteAddress + "]: ";
        Logger.info("A client has connected ( remote addr: " + request.remoteAddress + ")");
        final EventStream<Msg<ServerMsg>> serverEventStream = server.events.eventStream();
        Logger.info(s + "Client received event stream: " + serverEventStream + " from " + server.events);
        try{
            while(inbound.isOpen()){

                Logger.info(s + "Waiting for something ...");
                Either<Msg<ServerMsg>, WebSocketEvent> event = await(Promise.waitEither(
                        serverEventStream.nextEvent(),
                        inbound.nextEvent()
                ));
                Logger.info(s + "Some event arrived!");

                for(Msg<ServerMsg> serverMsg : F.Matcher.ClassOf(Msg.class).match(event._1)){
                    Logger.info(s + "Sending to client: " + serverMsg);
                    sendMessage(serverMsg);
                }

                for(Http.WebSocketFrame fromClient : F.Matcher.ClassOf(Http.WebSocketFrame.class).match(event._2)){
                    Msg<ClientMsg> msg = objectFromBytes(fromClient.binaryData);
                    Logger.info(s + "Received from client: " + msg);
                    serverEventStream.publish(new MsgText<ServerMsg>(ServerMsg.ONLINE_PLAYERS, "Someone said: '" + msg.toString() + "'!"));
                }

                for(WebSocketEvent fromClient : F.Matcher.ClassOf(Http.WebSocketClose.class).match(event._2)){
                    Logger.info(s + "Socket closed!");
                }
            }

        }catch(IOException e){
            Logger.info(s + "IOException: " + e.getMessage());
        }catch(ClassNotFoundException e){
            Logger.info(s + "ClassNotFound: " + e.getMessage());
        }
        Logger.info(s + "A client has disconnected");

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
        System.out.println("session id: " + s.getId());
        System.out.println("session auth-token: " + s.getAuthenticityToken());
    }



//    private static void handleMessage(Msg<ClientMsg> msg){
//        try {
////            System.out.println("request: " + request);
////            System.out.println("params: " + params);
////            System.out.println("validation: " + validation);
////            System.out.println("session: " + session);
//
//
////            printRequest(request);
//            System.out.println("req controller: " + request.controller);
//            System.out.println("req remoteAddr: " + request.remoteAddress);
//            printSession(session);
//            System.out.println();
//
//
//
//            System.out.println("Received msg: " + msg);
////            System.out.println("Sending reply...");
//            byte opcode = 0x2; //binary
//            TCP_Server.INSTANCE.TEST_VALUES.add("X" + new Random().nextInt(1000));
//
//            F.ArchivedEventStream f = null;
//            f.even
//
//
//            for(InOut inout : TCP_Server.INSTANCE.channels){
//                outbound;
//                inout.outbound.send(opcode, bytesFromObject(new MsgStringList(ServerMsg.ONLINE_PLAYERS, new ArrayList<String>(TCP_Server.INSTANCE.TEST_VALUES))));
//            }
//
//
//
//        } catch (IOException e1) {
//            e1.printStackTrace();
//        }
//    }
    
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
//                if(TCPServer.validPlayerName(name)) {
//
//                    sendMessage(new Msg(ServerMsg.OK));
//                    setName(name);
//                    TCPServer.addPlayer(name, thisSocket);
//                    System.out.println(name + " has logged in.");
//                    TCPServer.printState();
//                    isLoggedIn = true;
//                }else{
//                    thisSocket.sendMessage(new MsgText(ServerMsg.NO, "Invalid name!"));
//                }
//                break;
//
//            case ACCEPT_INVITE:
//                Lobby otherLobby = TCPServer.getLobbyOfHost(thisSocket.getInvitedBy());
//                otherLobby.setConnected(thisSocket.getName());
//                broadcastLobbyState(otherLobby);
//                thisSocket.joinLobby(otherLobby);
//                TCPServer.printState();
//                break;
//            case DECLINE_INVITE:
//                otherLobby = TCPServer.getLobbyOfHost(thisSocket.getInvitedBy());
//                thisSocket.removeInvite();
//                otherLobby.removePlayer(thisSocket.getName());
//                broadcastLobbyState(otherLobby);
//                TCPServer.printState();
//                break;
//
//            case LOGOUT:
//                TCPServer.removePlayer(thisSocket.getName());
//                break;
//            case CREATE_LOBBY:
//                Lobby newLobby = new Lobby(thisSocket.getName());
//                thisSocket.joinLobby(newLobby);
//                TCPServer.addLobby(thisSocket.getName(), newLobby);
//                TCPServer.printState();
//                break;
//            case INVITE_TO_LOBBY:
//                handleInvite((MsgText) msg);
//                break;
//            case ADD_BOT_TO_LOBBY:
//                String botName = TCPServer.generateBotName();
//
//                //The bot-socket has no dedicated thread like the human-sockets
//                BotSocket botSocket = new BotSocket(new AI(), botName);
//                TCPServer.addPlayer(botName, botSocket);
//                botSocket.joinLobby(thisSocket.getLobby());
//                thisSocket.getLobby().addPlayer(LobbyPlayer.bot(botName));
//                broadcastLobbyState(thisSocket.getLobby());
//                TCPServer.printState();
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
//                    TCPServer.createGameHostedBy(thisSocket.getName(), lobby.size(), lobby.numCols, lobby.numRows);
//                    for(LobbyPlayer bot : lobby.getAllBots()){
//                        TCPServer.joinGameHostedBy(thisSocket.getName(), bot.name);
//                    }
//                    TCPServer.printState();
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
//                boolean newGameStarted = TCPServer.joinGameHostedBy(host, thisSocket.getName());
//                if(newGameStarted){
//                    TCPServer.removeLobby(host);
//                }
//                thisSocket.leaveLobby();
//                TCPServer.printState();
//                return true; //Return from this runnable. It's job is done!
//
//            case QUICK_START_GAME:
//                MsgQuickStartGame quickStart = (MsgQuickStartGame) msg;
//                TCPServer.createGameHostedBy(thisSocket.getName(), 1 + quickStart.numBots, quickStart.numCols, quickStart.numRows);
//                List<String> names = new ArrayList<String>();
//                for(int i = 0; i < quickStart.numBots; i++){
//                    botName = TCPServer.generateBotName();
//                    botSocket = new BotSocket(new AI(), botName);
//                    names.add(botName);
//                    TCPServer.addPlayer(botName, botSocket);
//                    TCPServer.joinGameHostedBy(thisSocket.getName(), botName);
//                }
//                names.add(thisSocket.getName());
//                thisSocket.sendMessage(new MsgGameIsStarting(quickStart.numCols, quickStart.numRows, names.toArray(new String[0])));
//                TCPServer.printState();
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
//        invitedSocket = TCPServer.getSocket(invitedName);
//        playerFound = TCPServer.containsPlayer(invitedName) && invitedSocket.isRemote();
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
//            TCPServer.printState();
//        }
//    }
//
//    private void handleKick(MsgText kickMsg) throws IOException {
//        String kickedPlayer = kickMsg.text;
//        PlayerSocket kickedSocket = TCPServer.getSocket(kickedPlayer);
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
//                    TCPServer.removePlayer(bot.name);
//                }
//                TCPServer.removeLobby(socket.getName());
//            }else{
//                String newHost = otherHumansInLobby.get(0).name;
//                lobby.setNewHost(newHost);
//            }
//        }
//        broadcastLobbyState(lobby);
//        TCPServer.printState();
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
//                TCPServer.getSocket(playerInLobby).sendMessage(msg);
//            }
//        }
//    }


}