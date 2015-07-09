package controllers;

import fourword_shared.messages.*;
import fourword_shared.model.Lobby;
import play.Logger;
import play.libs.F;
import play.libs.F.*;
import play.mvc.Http;
import play.mvc.Http.WebSocketEvent;
import play.mvc.WebSocketController;

import controllers.ServerEvent.*;

import java.io.*;
import java.util.List;

public class ServerController extends WebSocketController {

//    private final static ThreadLocal<Boolean> isLoggedIn = new ThreadLocal<Boolean>();
    private final static Server server = Server.INSTANCE;
//    private final static ThreadLocal<String> myName = new ThreadLocal<String>();
//    private final static ThreadLocal<String> invitedBy = new ThreadLocal<String>();
//    private final static ThreadLocal<Lobby> myLobby = new ThreadLocal<Lobby>();


    public static void connect(String idString) {


        System.out.println(idString);

        if(idString == null || idString.length() == 0){
            System.out.println("idString: '" + idString + "'. Disconnecting!");

            disconnect();
            return;
        }

        UserId userId = new UserId(idString);


        Logger.info(userId + " connected ( remote addr: " + request.remoteAddress + ")");
        final EventStream<ServerEvent> serverEventStream = server.stream();
//        Logger.info(s + "Client received event stream: " + serverEventStream + " from " + server.events);
        try{
            while(inbound.isOpen()){

                System.out.println(userId + " waiting ...");
//                Logger.info(s + "Waiting for something ...");
                Either<ServerEvent, WebSocketEvent> event = await(Promise.waitEither(
                        serverEventStream.nextEvent(),
                        inbound.nextEvent()
                ));

//                Logger.info(s + "Some event arrived!");

                for(ServerEvent serverEvent : F.Matcher.ClassOf(ServerEvent.class).match(event._1)){
//                    Logger.info(s + "Sending to client: " + serverMsg);

                    System.out.println(userId + " received server event.");
                    handleServerEvent(userId, serverEvent);
                }

                for(Http.WebSocketFrame fromClient : F.Matcher.ClassOf(Http.WebSocketFrame.class).match(event._2)){
                    Msg<ClientMsg> msg = objectFromBytes(fromClient.binaryData);
                    System.out.println(userId + " received from client: " + msg);
                    handleClientMessage(userId, msg);
                }

                for(Http.WebSocketClose fromClient : F.Matcher.ClassOf(Http.WebSocketClose.class).match(event._2)){
                    System.out.println(userId + " socket closed.");
                }

//                Logger.info(s + "The event was: " + event);

            }

        }catch(IOException e){
            Logger.info(userId + "   IOException: ");
            e.printStackTrace();
        }catch(ClassNotFoundException e){
            Logger.info(userId + "   ClassNotFound: ");
            e.printStackTrace();
        }
        Logger.info(userId + "   disconnected");
        server.playerLoggedOut(userId);

    }

    private static <R> Option<R> match(Class<R> clazz, Object o){
        return F.Matcher.ClassOf(clazz).match(o);
    }

    private static void handleServerEvent(UserId userId, ServerEvent e) throws IOException {

        for(InvitedToLobby invitedEvent : match(InvitedToLobby.class, e)){
            if(server.isPlayerInLobby(userId, invitedEvent.lobby)){
                if(invitedEvent.invitedId.equals(userId)){
                    sendMessage(new MsgText(ServerMsg.YOU_ARE_INVITED, invitedEvent.inviter));
                }else{
                    sendMessage(new MsgLobbyState(invitedEvent.lobby));
                }
            }
        }

        for(JoinedLobby joinedEvent : match(JoinedLobby.class, e)){
            notifyLobbyStateIfMyLobby(userId, joinedEvent.lobby);
        }

        for(KickedFromLobby kickedEvent : match(KickedFromLobby.class, e)){
            if(server.isPlayerInLobby(userId, kickedEvent.lobby)){
                if(kickedEvent.kicked.equals(userId)){
                    sendMessage(new Msg(ServerMsg.YOU_WERE_KICKED));
                }else{
                    sendMessage(new MsgLobbyState(kickedEvent.lobby));
                }
            }
        }

        for(LobbyDimensionsChanged dimEvent : match(LobbyDimensionsChanged.class, e)){
            notifyLobbyStateIfMyLobby(userId, dimEvent.lobby);
        }


    }

    private static void notifyLobbyStateIfMyLobby(UserId userId, Lobby lobby) throws IOException {
        if(server.isPlayerInLobby(userId, lobby)){
            sendMessage(new MsgLobbyState(lobby));
        }
    }


    private static void handleClientMessage(UserId userId, Msg<ClientMsg> msg) throws IOException {
        String s = "[" + request.remoteAddress + "]: ";
        switch (msg.type()){

            case LOGIN:
                String loginName = ((MsgText)msg).text;
                Msg reply = server.tryLogin(userId, loginName);
                sendMessage(reply);
                break;

            case LOGOUT:
                server.playerLoggedOut(userId);
                break;

            case CREATE_LOBBY:
                server.createLobby(userId);
                break;

            case INVITE_TO_LOBBY:
                String invitedName = ((MsgText)msg).text;
                reply = server.tryInviteToLobby(userId, invitedName);
                sendMessage(reply);
                break;

            case ACCEPT_INVITE:
                server.acceptInvite(userId);
                break;

            case DECLINE_INVITE:
                server.declineInvite(userId);
                break;

            case ADD_BOT_TO_LOBBY:
                server.addBotToLobby(userId);
                break;

            case KICK_FROM_LOBBY:
                String kickedPlayer = ((MsgText)msg).text;
                server.kickFromLobby(userId, kickedPlayer);
                break;

            case LEAVE_LOBBY:
                server.leaveLobby(userId);
                break;

            case START_GAME_FROM_LOBBY:
                reply = server.tryStartGameFromLobby(userId);
                if(reply != null){
                    sendMessage(reply);
                }
                break;

            case LOBBY_SET_DIMENSIONS:
                server.changeLobbyDimensions(userId, ((MsgLobbySetDim) msg).numCols, ((MsgLobbySetDim) msg).numRows);
                break;

            case CONFIRM_GAME_STARTING:
                String host = ((MsgText)msg).text;
                server.joinGame(userId, host);
                break;

            case QUICK_START_GAME:
                MsgQuickStartGame quickStart = (MsgQuickStartGame) msg;
                List<String> names = server.createGameAndFillWithBots(userId, 1 + quickStart.numBots, quickStart.numCols, quickStart.numRows);
                sendMessage(new MsgGameIsStarting(quickStart.numCols, quickStart.numRows, names.toArray(new String[0])));
                break;
        }
//        return false; //Thread is not done yet
    }

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