package controllers;

import fourword_shared.messages.*;
import fourword_shared.model.*;
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

        if(idString == null || idString.length() == 0){
            System.out.println("idString: '" + idString + "'. Disconnecting!");

            disconnect();
            return;
        }

        UserId userId = new UserId(idString);

        Logger.info("Connected: " + userId + " ( remote addr: " + request.remoteAddress + ")");
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
                    handleServerEvent(userId, serverEvent);
                }

                for(Http.WebSocketFrame fromClient : F.Matcher.ClassOf(Http.WebSocketFrame.class).match(event._2)){
                    Msg<ClientMsg> msg = objectFromBytes(fromClient.binaryData);
                    System.out.println("from " + userId + ": " + msg);
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
        }catch (Exception e){
            Logger.info(userId + " EXCEPTION: ");
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
                    if(server.isPlayerInMenuOrLobbyHost(userId)){
                        LoggedIn loggedIn = (LoggedIn) e;
                        sendMessage(userId, new Msg.PlayerInfoUpdate(PlayerInfo.inMenu(loggedIn.player)));
                    }
                    break;
                case LOGOUT:
                    if(server.isPlayerInMenuOrLobbyHost(userId)) {
                        LoggedOut loggedOut = (LoggedOut) e;
                        sendMessage(userId, new Msg.PlayerLoggedOut(loggedOut.player));
                    }
                    break;
                case CREATE_LOBBY:
                    if(server.isPlayerInMenuOrLobbyHost(userId)) {
                        CreatedLobby createdLobby = (CreatedLobby) e;
                        sendMessage(userId, new Msg.PlayerInfoUpdate(PlayerInfo.inLobby(createdLobby.creatorName, true, 0)));
                    }
                    break;
                case INVITED_TO_LOBBY:
                    InvitedToLobby invitedEvent = (InvitedToLobby) e;
                    if(server.isPlayerInLobby(userId, invitedEvent.lobby)){
                        if(invitedEvent.invitedId.equals(userId)){
                            sendMessage(userId, new Msg.YouAreInvited(invitedEvent.inviter));
                        }else{
                            sendMessage(userId, new Msg.LobbyStateChange(Msg.LobbyStateChange.Action.INVITED, invitedEvent.invitedName));
                        }
                    }
                    break;
                case DECLINED_INVITE:
                    DeclinedInvite declinedEvent = (DeclinedInvite) e;
                    if(server.isPlayerInLobby(userId, declinedEvent.lobby)){
                        sendMessage(userId, new Msg.LobbyStateChange(Msg.LobbyStateChange.Action.LEFT, declinedEvent.player));
                    }
//                    notifyLobbyStateIfMyLobby(userId, (((DeclinedInvite)e).lobby));
                    break;
                case JOINED_LOBBY:
                    JoinedLobby joinedEvent = (JoinedLobby) e;
//                    notifyLobbyStateIfMyLobby(userId, joinedEvent.lobby);
                    if(server.isPlayerInLobby(userId, joinedEvent.lobby)){
                        if(userId.equals(joinedEvent.playerId)){
                            sendMessage(userId, new Msg.LobbyState(joinedEvent.lobby));
                        }else{
                            if(joinedEvent.isHuman){
                                sendMessage(userId, new Msg.LobbyStateChange(Msg.LobbyStateChange.Action.ACCEPTED_INVITE, joinedEvent.playerName));
                            }else{
                                sendMessage(userId, new Msg.LobbyStateChange(Msg.LobbyStateChange.Action.BOT_ADDED, joinedEvent.playerName));
                            }

                        }
                    }
                    notifyLobbyPlayerInfoIfRelevant(userId, (((JoinedLobby) e).lobby));
                    break;
                case KICKED_FROM_LOBBY:
                    KickedFromLobby kickedEvent = (KickedFromLobby) e;
                    if(userId.equals(kickedEvent.kickedId)) {
                        sendMessage(userId, new Msg.YouWereKicked());
                    }
                    if(server.isPlayerInLobby(userId, kickedEvent.lobby)){
                        sendMessage(userId, new Msg.LobbyStateChange(Msg.LobbyStateChange.Action.LEFT, kickedEvent.kickedName));
                    }
//                    notifyLobbyStateIfMyLobby(userId, kickedEvent.lobby);
                    if(server.isPlayerInMenu(userId)){
                        PlayerId kicked = kickedEvent.kickedId;
                        if(server.isLoggedInHuman(kicked)){
                            PlayerInfo info = server.getPlayerInfo((UserId) kicked);
                            sendMessage(userId, new Msg.PlayerInfoUpdate(info));
                        }
                    }
                    notifyLobbyPlayerInfoIfRelevant(userId, kickedEvent.lobby);
                    break;


                case LEFT_LOBBY:
                    LeftLobby leftEvent = (LeftLobby) e;
                    if(server.isPlayerInLobby(userId, leftEvent.lobby)){
                        sendMessage(userId, new Msg.LobbyStateChange(Msg.LobbyStateChange.Action.LEFT, leftEvent.playerName));
                    }
//                    notifyLobbyStateIfMyLobby(userId, leftEvent.lobby);
                    if(server.isPlayerInMenuOrLobbyHost(userId)){
                        PlayerId leaver = leftEvent.playerId;
                        if(server.isLoggedInHuman(leaver)){
                            PlayerInfo info = server.getPlayerInfo((UserId) leaver);
                            sendMessage(userId, new Msg.PlayerInfoUpdate(info));
                        }
                    }
                    notifyLobbyPlayerInfoIfRelevant(userId, leftEvent.lobby);
                    break;
                case LOBBY_GAME_STARTING:
                    LobbyGameStarting gameStarting = (LobbyGameStarting) e;
                    Lobby lobby = gameStarting.lobby;
                    if(server.isPlayerInLobby(userId, lobby)){
                        sendMessage(userId, new Msg.GameIsStarting(lobby.getSettings(), lobby.sortedNames().toArray(new String[0])));
                    }
                    break;
                case GAME_PLAYERS_TURN:
                    GamePlayersTurn playersTurn = (GamePlayersTurn) e;
                    if(server.isPlayerInGame(userId, playersTurn.game)){
                        sendMessage(userId, new Msg.PlayersTurn(playersTurn.playerName));
                        if(userId.equals(playersTurn.playerId)){
                            sendMessage(userId, new Msg.RequestPickAndPlaceLetter());
                        }
                    }
                    break;
                case GAME_HAS_STARTED:
                    notifyGamePlayerInfoIfRelevant(userId, ((GameHasStarted)e).game);
                    break;
                case LOBBY_REMOVED:
                    break;
                case LOBBY_NEW_HOST:
                    LobbyNewHost newHost = (LobbyNewHost) e;
                    if(server.isPlayerInLobby(userId, newHost.lobby)){
                        sendMessage(userId, new Msg.LobbyStateChange(Msg.LobbyStateChange.Action.NEW_HOST, newHost.playerName));
                    }
//                    notifyLobbyStateIfMyLobby(userId, (((LobbyNewHost)e).lobby));
                    break;
                case GAME_FINISHED:
                    GameFinished gameFinished = (GameFinished) e;
                    GameObject game = gameFinished.game;
                    if(server.isPlayerInGame(userId, game)){
                        GameResult result = game.getResult();
                        sendMessage(userId, new Msg.GameFinished(result)); //TODO
                    }
                    notifyGamePlayerInfoIfRelevant(userId, game);
                    break;
                case GAME_ENDED_EARLY:
                    GameEnded endedEvent = (GameEnded) e;
                    game = endedEvent.game;
                    //Don't tell the leaver the game ended, since the leaver is already in Menu
                    if(server.isPlayerInGame(userId, game) && !endedEvent.leaverId.equals(userId)){
                        sendMessage(userId, new Msg.GameEnded(endedEvent.leaverName));
                    }
                    notifyGamePlayerInfoIfRelevant(userId, game);
                    break;
//                case GAME_CRASHED: //not triggered from Server
//                    GameCrashed crashedEvent = (GameCrashed) e;
//                    game = crashedEvent.game;
//                    if(server.isPlayerInGame(userId, game)){
//                        sendMessage(new Msg.GameCrashed());
//                    }
//                    notifyGamePlayerInfoIfRelevant(userId, game);
//                    break;

                case GAME_CRASHED:
                    break;
                case PICKED_AND_PLACED_LETTER:
                    PickedAndPlacedLetter papEvent = (PickedAndPlacedLetter) e;
                    if(server.isPlayerInGame(userId, papEvent.game)){
                        sendMessage(userId, new Msg.PlayerDoneThinking(papEvent.playerName));
                        if(!server.isUserPlayer(userId, papEvent.playerName)){
                            sendMessage(userId, new Msg.RequestPlaceLetter(papEvent.letter, papEvent.playerName));
                        }
                    }
                    break;
                case PLACED_LETTER:
                    PlacedLetter pEvent = (PlacedLetter) e;
                    if(server.isPlayerInGame(userId, pEvent.game)){
                        sendMessage(userId, new Msg.PlayerDoneThinking(pEvent.playerName));
                    }
                    break;

                case LOBBY_SET_VAR:
                    LobbySetVar setVar = (LobbySetVar) e;
                    if(server.isPlayerInLobby(userId, setVar.lobby)){
                        sendMessage(userId, Msg.LobbySetAttribute.serverMsg(setVar.var, setVar.value));
                    }
                    break;

                case GAME_SERVER_SET_LETTER:
                    GameServerSetLetter setLetter = (GameServerSetLetter) e;
                    game = setLetter.game;
                    if(server.isPlayerInGame(userId, game)){
                        sendMessage(userId, new Msg.SetLetterAtCell(setLetter.cell, setLetter.letter));
                    }
                    break;
            }
        }
    }

    private static void notifyGamePlayerInfoIfRelevant(UserId userId, GameObject game) throws IOException {
        if(server.isPlayerInMenu(userId) || server.isPlayerHostingLobby(userId)){
            List<PlayerInfo> infos = server.getPlayerInfoForAllInGame(game);
            for(PlayerInfo info : infos){
                sendMessage(userId, new Msg.PlayerInfoUpdate(info));
            }
        }
    }

    private static void notifyLobbyPlayerInfoIfRelevant(UserId userId, Lobby lobby) throws IOException {
        if(server.isPlayerInMenu(userId) || server.isPlayerHostingLobby(userId)){
            List<PlayerInfo> infos = server.getPlayerInfoForAllInLobby(lobby);
            for(PlayerInfo info : infos){
                sendMessage(userId, new Msg.PlayerInfoUpdate(info));
            }
        }
    }

//    private static void notifyLobbyStateIfMyLobby(UserId userId, Lobby lobby) throws IOException {
//        if(server.isPlayerInLobby(userId, lobby)){
//            sendMessage(userId, new Msg.LobbyState(lobby));
//        }
//    }


    private static void handleClientMessage(UserId userId, Msg<ClientMsg> msg) throws IOException {
        String s = "[" + request.remoteAddress + "]: ";
        switch (msg.type()){

            case LOGIN:
                String loginName = ((Msg.LogIn)msg).get();
                Msg reply = server.tryLogin(userId, loginName);
                sendMessage(userId, reply);
                break;

            case LOGOUT:
                server.safePlayerLoggedOut(userId);
                break;

            case LOBBY_SET_ATTRIBUTE:
                Msg.LobbySetAttribute<ClientMsg> setVar = (Msg.LobbySetAttribute<ClientMsg>) msg;
                server.lobbySetVar(userId, setVar.attribute, setVar.value);
                break;

            case CREATE_LOBBY:
                server.createLobby(userId);
                break;

            case INVITE_TO_LOBBY:
                String invitedName = ((Msg.InviteToLobby)msg).get();
                reply = server.tryInviteToLobby(userId, invitedName);
                sendMessage(userId, reply);
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

            case LEAVE_GAME:
                server.leaveGame(userId);
                break;

            case START_GAME_FROM_LOBBY:
                reply = server.tryStartGameFromLobby(userId);
                if(reply != null){
                    sendMessage(userId, reply);
                }
                break;

            case CONFIRM_GAME_STARTING:
                String host = ((Msg.ConfirmGameStarting)msg).get();
                server.joinGameHuman(userId, host);
                break;

            case REQUEST_ONLINE_PLAYERS_INFO:
                sendMessage(userId, new Msg.OnlinePlayersInfo(server.onlinePlayers()));
                break;

            case QUICK_START_GAME:
                Msg.QuickStartGame quickStart = (Msg.QuickStartGame) msg;
                GameSettings settings = new GameSettings();
                List<String> names = server.createGameAndFillWithBots(userId, 1 + quickStart.numBots, quickStart.gameSettings);
                sendMessage(userId, new Msg.GameIsStarting(quickStart.gameSettings, names.toArray(new String[0])));
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


    private static void sendMessage(UserId userId, Msg<ServerMsg> msg) throws IOException {
        System.out.println("To " + userId + " : " + msg);
        byte opcode = 0x2; //binary
        outbound.send(opcode, bytesFromObject(msg));
    }


}