package controllers;

import fourword_shared.messages.*;
import fourword_shared.model.Lobby;
import fourword_shared.model.LobbyPlayer;
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
import java.util.List;
import java.util.Random;


public class ServerController extends WebSocketController {

    private static boolean isLoggedIn;
    private static Server server = Server.INSTANCE;
    private static String name = "DummyName";
    private static String invitedBy = "DummyNameInviter";
    private static Lobby currentLobby = null;


    public static void connect() {
        String s = "[" + request.remoteAddress + "]: ";
        Logger.info(s + "connected ( remote addr: " + request.remoteAddress + ")");
        final EventStream<ServerEvent> serverEventStream = server.stream();
//        Logger.info(s + "Client received event stream: " + serverEventStream + " from " + server.events);
        try{
            while(inbound.isOpen()){

//                Logger.info(s + "Waiting for something ...");
                Either<ServerEvent, WebSocketEvent> event = await(Promise.waitEither(
                        serverEventStream.nextEvent(),
                        inbound.nextEvent()
                ));
//                Logger.info(s + "Some event arrived!");

                for(ServerEvent serverEvent : F.Matcher.ClassOf(ServerEvent.class).match(event._1)){
//                    Logger.info(s + "Sending to client: " + serverMsg);
                    handleServerEvent(serverEvent);
                }

                for(Http.WebSocketFrame fromClient : F.Matcher.ClassOf(Http.WebSocketFrame.class).match(event._2)){
                    Msg<ClientMsg> msg = objectFromBytes(fromClient.binaryData);
                    Logger.info(s + "Received from client: " + msg);
                    handleClientMessage(msg);
                }

                for(Http.WebSocketClose fromClient : F.Matcher.ClassOf(Http.WebSocketClose.class).match(event._2)){
                    Logger.info(s + "Socket closed!");
                }

//                Logger.info(s + "The event was: " + event);


            }

        }catch(IOException e){
            Logger.info(s + "IOException: " + e.getMessage());
        }catch(ClassNotFoundException e){
            Logger.info(s + "ClassNotFound: " + e.getMessage());
        }
        Logger.info(s + "disconnected");

    }

    private static void handleServerEvent(ServerEvent e){

    }


    private static void handleClientMessage(Msg<ClientMsg> msg) throws IOException {
        switch (msg.type()){

            case LOGIN:
                if(isLoggedIn){
                    throw new RuntimeException();
                }
                String loginName = ((MsgText)msg).text;
                if(server.validPlayerName(loginName)) {
                    sendMessage(new Msg(ServerMsg.OK));
                    name = loginName;
                    server.playerLoggedIn(loginName);
                    isLoggedIn = true;
                }else{
                    sendMessage(new MsgText(ServerMsg.NO, "Invalid name!"));
                }
                break;

            case LOGOUT:
                server.playerLoggedOut(name);
                break;

            case CREATE_LOBBY:
                Lobby newLobby = new Lobby(name);
                invitedBy = null;
                currentLobby = newLobby;
                server.addLobby(name, newLobby);
                break;

            case INVITE_TO_LOBBY:
                handleInvite((MsgText) msg);
                break;

            case ACCEPT_INVITE:
                Lobby otherLobby = server.getLobbyOfHost(invitedBy);
                server.acceptInvite(name, otherLobby);
//                otherLobby.setConnected(name);
//                broadcastLobbyState(otherLobby);
                currentLobby = otherLobby;
                invitedBy = null;
                break;

            case DECLINE_INVITE:
                otherLobby = server.getLobbyOfHost(invitedBy);
                invitedBy = null;
                server.declineInvite(name, otherLobby);
//                otherLobby.removePlayer(name);
//                broadcastLobbyState(otherLobby);
//                server.printState();
                break;



            case ADD_BOT_TO_LOBBY:
//                String botName = server.generateBotName();

                //The bot-socket has no dedicated thread like the human-sockets
//                BotSocket botSocket = new BotSocket(new AI(), botName);
                server.addBotToLobby(currentLobby);
//                server.addPlayer(botName);
//                botSocket.joinLobby(thisSocket.getLobby());
//                currentLobby.addPlayer(LobbyPlayer.bot(botName));
//                broadcastLobbyState(currentLobby);
//                server.printState();
                break;
            case KICK_FROM_LOBBY:
                String kickedPlayer = ((MsgText)msg).text;
                server.kickFromLobby(name, kickedPlayer);
                break;
            case LEAVE_LOBBY:
                Lobby lobby = currentLobby;
                currentLobby = null;
                server.leaveLobby(name, lobby);
                break;
            case START_GAME_FROM_LOBBY:
                lobby = currentLobby;
                boolean enoughPlayers = lobby.size() > 1;
                if(enoughPlayers){
                    server.startGameFromLobby(lobby);
                }else{
                    sendMessage(new MsgText(ServerMsg.NO, "Not enough playerNames!"));
                }

                break;

            case LOBBY_SET_DIMENSIONS:
                currentLobby.numRows = ((MsgLobbySetDim)msg).numRows;
                currentLobby.numCols = ((MsgLobbySetDim)msg).numCols;
                server.lobbyDimensionsChanged(currentLobby);
//                broadcastLobbyState(currentLobby);
                break;

            case CONFIRM_GAME_STARTING:
                String host = ((MsgText)msg).text;
                boolean newGameStarted = server.joinGameHostedBy(host, name);
                if(newGameStarted){
                    server.removeLobby(host);
                }
//                server.leaveLobby(name, currentLobby);
//                server.printState();
//                return true; //Return from this runnable. It's job is done!
                break;

            case QUICK_START_GAME:
                MsgQuickStartGame quickStart = (MsgQuickStartGame) msg;
                List<String> names = server.createGameAndFillWithBots(name, 1 + quickStart.numBots, quickStart.numCols, quickStart.numRows);
//                List<String> names = new ArrayList<String>();
//                for(int i = 0; i < quickStart.numBots; i++){
//                    botName = server.generateBotName();
//                    botSocket = new BotSocket(new AI(), botName);
//                    names.add(botName);
//                    server.addPlayer(botName);
//                    server.joinGameHostedBy(name, botName);
//                }
//                names.add(name);
                sendMessage(new MsgGameIsStarting(quickStart.numCols, quickStart.numRows, names.toArray(new String[0])));
//                server.printState();
//                return false;
                break;
        }
//        return false; //Thread is not done yet
    }

    private static void handleInvite(MsgText inviteMsg) throws IOException {
        String invitedName = inviteMsg.text;
        String inviterName = name;
        boolean playerFound, selfInvite;
        PlayerSocket invitedSocket;

        playerFound = server.containsPlayer(invitedName); //TODO check is not BOT
        selfInvite = inviterName.equals(invitedName);
        if(selfInvite){
            sendMessage(new MsgText(ServerMsg.NO, "You can't invite yourself!"));
        }else if(!playerFound){
            sendMessage(new MsgText(ServerMsg.NO, "Can't find that player!"));
//        }else if(invitedSocket.isInvited()) { //TODO Handle player is already invited
//            sendMessage(new MsgText(ServerMsg.NO, "That player already has a pending invite!"));
//        }else if(invitedSocket.isInLobby()) {//TODO Handle player is in obby
//            thisSocket.sendMessage(new MsgText(ServerMsg.NO, "That player is already in a lobby!"));
        }else{
            sendMessage(new Msg(ServerMsg.OK));
            server.inviteToLobby(name, currentLobby, invitedName);
//            currentLobby.addPlayer(LobbyPlayer.pendingHuman(invitedName));
//            invitedSocket.sendMessage(new MsgText(ServerMsg.YOU_ARE_INVITED, inviterName));
            //TODO notify server that that player has been invited, so he can be notified with a msg
//            broadcastLobbyState(currentLobby);
//            invitedSocket.setInvitedBy(inviterName);
//            server.printState();
        }
    }

//    private static void handleKick(MsgText kickMsg) throws IOException {
//        String kickedPlayer = kickMsg.text;
//        server.kickFromLobby(kickedPlayer);
////        PlayerSocket kickedSocket = server.getSocket(kickedPlayer);
////        kickedSocket.sendMessage(new Msg(ServerMsg.YOU_WERE_KICKED));
////        leaveLobby(kickedSocket); //notify server that the player was kicked
//    }

//    private static void leaveLobby() throws IOException {
//        Lobby lobby = currentLobby;
//        currentLobby = null;
//        server.leaveLobby(name, lobby);
////        boolean isHost = lobby.getHost().equals(name);
////        lobby.removePlayer(name);
////        if(isHost){
////            ArrayList<LobbyPlayer> otherHumansInLobby = lobby.getAllHumans();
////            if(otherHumansInLobby.isEmpty()){
//////                for(LobbyPlayer bot : lobby.getAllBots()){
//////                    server.removePlayer(bot.name);
//////                }
////                server.removeLobby(name);
////            }else{
////                String newHost = otherHumansInLobby.get(0).name;
////                lobby.setNewHost(newHost);
////            }
////        }
////        broadcastLobbyState(lobby);
////        server.printState();
//    }









    private  static <T> T objectFromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        return (T) new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject();
    }

    private static byte[] bytesFromObject(Object obj) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ObjectOutputStream(out).writeObject(obj);
        return out.toByteArray();
    }


    private static void sendMessage(Msg<ServerMsg> msg) throws IOException {
        byte opcode = 0x2; //binary
        outbound.send(opcode, bytesFromObject(msg));
    }


}