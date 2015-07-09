package controllers;

import fourword_shared.messages.*;
import fourword_shared.model.Lobby;
import fourword_shared.model.MockupFactory;
import fourword_shared.model.PlayerInfo;
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
        server.safePlayerLoggedOut(userId);

    }

    private static <R> Option<R> match(Class<R> clazz, Object o){
        return F.Matcher.ClassOf(clazz).match(o);
    }

    private static void handleServerEvent(UserId userId, ServerEvent e) throws IOException {
        if(server.isUserOnline(userId)){
            switch(e.type){
                case LOGIN:
                    if(server.isPlayerInMenu(userId)){
                        LoggedIn loggedIn = (LoggedIn) e;
                        sendMessage(new Msg.PlayerInfoUpdate(PlayerInfo.inMenu(loggedIn.player)));
                    }
                    break;
                case LOGOUT:
                    if(server.isPlayerInMenu(userId)) {
                        LoggedOut loggedOut = (LoggedOut) e;
                        sendMessage(new Msg.PlayerLoggedOut(loggedOut.player));
                    }
                    break;
                case CREATE_LOBBY:
                    if(server.isPlayerInMenu(userId)) {
                        CreatedLobby createdLobby = (CreatedLobby) e;
                        sendMessage(new Msg.PlayerInfoUpdate(PlayerInfo.inLobby(createdLobby.creatorName, true, 0)));
                    }
                    break;
                case INVITED_TO_LOBBY:
                    ServerEvent.InvitedToLobby invitedEvent = (InvitedToLobby) e;
                    if(server.isPlayerInLobby(userId, invitedEvent.lobby)){
                        if(invitedEvent.invitedId.equals(userId)){
                            sendMessage(new Msg.YouAreInvited(invitedEvent.inviter));
                        }else{
                            sendMessage(new Msg.LobbyState(invitedEvent.lobby));
                        }
                    }
                    break;

                case KICKED_FROM_LOBBY:
                    KickedFromLobby kickedEvent = (KickedFromLobby) e;
                    if(kickedEvent.kicked.equals(userId)) {
                        sendMessage(new Msg.YouWereKicked());
                    }
                    notifyLobbyStateIfMyLobby(userId, kickedEvent.lobby);
                    notifyLobbyPlayerInfoIfInMenu(userId, kickedEvent.lobby);
                    break;
                case DECLINED_INVITE:
                    notifyLobbyStateIfMyLobby(userId, (((DeclinedInvite)e).lobby));
                    break;
                case JOINED_LOBBY:
                    notifyLobbyStateIfMyLobby(userId, (((JoinedLobby)e).lobby));
                    notifyLobbyPlayerInfoIfInMenu(userId, (((JoinedLobby)e).lobby));
                    break;
                case LEFT_LOBBY:
                    notifyLobbyStateIfMyLobby(userId, (((LeftLobby)e).lobby));
                    notifyLobbyPlayerInfoIfInMenu(userId, (((LeftLobby)e).lobby));
                    break;
                case LOBBY_GAME_STARTING:
                    LobbyGameStarting gameStarting = (LobbyGameStarting) e;
                    Lobby lobby = gameStarting.lobby;
                    if(server.isPlayerInLobby(userId, lobby)){
                        sendMessage(new Msg.GameIsStarting(lobby.numCols, lobby.numRows, lobby.sortedNames().toArray(new String[0])));
                    }
                    break;
                case GAME_PLAYERS_TURN:
                    GamePlayersTurn playersTurn = (GamePlayersTurn) e;
                    if(server.isPlayerInGame(userId, playersTurn.game)){
                        sendMessage(new Msg.PlayersTurn(playersTurn.playerName));
                        if(userId.equals(playersTurn.playerId)){
                            sendMessage(new Msg.RequestPickAndPlaceLetter());
                        }
                    }
                    break;
                case GAME_HAS_STARTED:

                    notifyGamePlayerInfoIfInMenu(userId, ((GameHasStarted) e).game);
                    break;
                case LOBBY_REMOVED:
                    break;
                case LOBBY_DIM_CHANGE:
                    notifyLobbyStateIfMyLobby(userId, (((LobbyDimensionsChanged)e).lobby));
                    break;
                case LOBBY_NEW_HOST:
                    notifyLobbyStateIfMyLobby(userId, (((LobbyNewHost)e).lobby));
                    break;
                case GAME_FINISHED:
                    GameFinished gameFinished = (GameFinished) e;
                    if(server.isPlayerInGame(userId, gameFinished.game)){
                        sendMessage(new Msg.GameFinished(MockupFactory.createResult())); //TODO
                    }

                    //TODO tell menu sitters aobut the player info changes
                    break;
                case GAME_CRASHED:
                    break;


                case PICKED_AND_PLACED_LETTER:
                    PickedAndPlacedLetter papEvent = (PickedAndPlacedLetter) e;
                    if(server.isPlayerInGame(userId, papEvent.game)){
                        sendMessage(new Msg.PlayerDoneThinking(papEvent.playerName));
                        if(!server.isUserPlayer(userId, papEvent.playerName)){
                            sendMessage(new Msg.RequestPlaceLetter(papEvent.letter, papEvent.playerName));
                        }
                    }
                    break;
                case PLACED_LETTER:
                    PlacedLetter pEvent = (PlacedLetter) e;
                    if(server.isPlayerInGame(userId, pEvent.game)){
                        sendMessage(new Msg.PlayerDoneThinking(pEvent.playerName));
                    }
                    break;


            }
        }
    }

    private static void notifyGamePlayerInfoIfInMenu(UserId userId, GameObject game) throws IOException {
        if(server.isPlayerInMenu(userId)){
            List<PlayerInfo> infos = server.getPlayerInfoForAllInGame(game);
            for(PlayerInfo info : infos){
                sendMessage(new Msg.PlayerInfoUpdate(info));
            }
        }
    }

    private static void notifyLobbyPlayerInfoIfInMenu(UserId userId, Lobby lobby) throws IOException {
        if(server.isPlayerInMenu(userId)){
            List<PlayerInfo> infos = server.getPlayerInfoForAllInLobby(lobby);
            for(PlayerInfo info : infos){
                sendMessage(new Msg.PlayerInfoUpdate(info));
            }
        }
    }

    private static void notifyLobbyStateIfMyLobby(UserId userId, Lobby lobby) throws IOException {
        if(server.isPlayerInLobby(userId, lobby)){
            sendMessage(new Msg.LobbyState(lobby));
        }
    }


    private static void handleClientMessage(UserId userId, Msg<ClientMsg> msg) throws IOException {
        String s = "[" + request.remoteAddress + "]: ";
        switch (msg.type()){

            case LOGIN:
                String loginName = ((Msg.LogIn)msg).get();
                Msg reply = server.tryLogin(userId, loginName);
                sendMessage(reply);
                break;

            case LOGOUT:
                server.safePlayerLoggedOut(userId);
                break;

            case CREATE_LOBBY:
                server.createLobby(userId);
                break;

            case INVITE_TO_LOBBY:
                String invitedName = ((Msg.InviteToLobby)msg).get();
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
                String kickedPlayer = ((Msg.KickFromLobby)msg).get();
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
                server.changeLobbyDimensions(userId, ((Msg.LobbySetDim) msg).numCols, ((Msg.LobbySetDim) msg).numRows);
                break;

            case LEAVE_GAME:
                break;

            case CONFIRM_GAME_STARTING:
                String host = ((Msg.ConfirmGameStarting)msg).get();
                server.joinGameHuman(userId, host);
                break;

            case REQUEST_ONLINE_PLAYERS_INFO:
                sendMessage(new Msg.OnlinePlayersInfo(server.onlinePlayers()));
                break;

            case QUICK_START_GAME:
                Msg.QuickStartGame quickStart = (Msg.QuickStartGame) msg;
                List<String> names = server.createGameAndFillWithBots(userId, 1 + quickStart.numBots, quickStart.numCols, quickStart.numRows);
                sendMessage(new Msg.GameIsStarting(quickStart.numCols, quickStart.numRows, names.toArray(new String[0])));
                break;

            case PLACE_LETTER:
                Msg.PlaceLetter placeLetter = (Msg.PlaceLetter) msg;
                server.playerPlacedLetter(userId, placeLetter.get());
                break;

            case PICK_AND_PLACE_LETTER:
                Msg.PickAndPlaceLetter pickAndPlace = (Msg.PickAndPlaceLetter) msg;
                server.playerPickedAndPlacedLetter(userId, pickAndPlace.letter, pickAndPlace.cell);
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