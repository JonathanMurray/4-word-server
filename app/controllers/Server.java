package controllers;

import fourword_shared.messages.*;
import fourword_shared.model.Lobby;
import fourword_shared.model.LobbyPlayer;
import play.libs.F;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by jonathan on 2015-07-08.
 */
public class Server implements ServerGameBehaviour.GameListener {

    public final static Server INSTANCE = new Server();
    public final static int MAX_ARCHIVED_EVENTS = 100;

    private F.ArchivedEventStream<ServerEvent> events = new F.ArchivedEventStream(MAX_ARCHIVED_EVENTS);

    F.EventStream<ServerEvent> stream(){
        return events.eventStream();
    }

    private List<String> onlinePlayers = new ArrayList<String>();

    private HashMap<String, Lobby> hostLobbyMap = new HashMap<String, Lobby>();
    private HashMap<String, GameObject> hostGameMap = new HashMap();

    void createGameHostedBy(String hostName, int numPlayers, int numCols, int numRows){
        hostGameMap.put(hostName, new GameObject(numPlayers, hostName, numCols, numRows));
    }

    List<String> createGameAndFillWithBots(String hostName, int numPlayers, int numCols, int numRows){
        createGameHostedBy(hostName, numPlayers, numCols, numRows);
        int numBots = numPlayers - 1;
        List<String> names = new ArrayList<String>();
        names.add(hostName);
        for(int i = 0; i < numBots; i++){
            String botName = generateBotName(names);
            names.add(botName);
            joinGameHostedBy(hostName, botName);
        }
        return names;
    }

    boolean joinGameHostedBy(String hostName, String joiningPlayer){
        GameObject game = hostGameMap.get(hostName);
        game.join(joiningPlayer);
        if(game.isReadyToStart()){
            ServerGameBehaviour gameThread = new ServerGameBehaviour(this, game);
            new Thread(gameThread).start();
            return true;
        }
        return false;
    }

    String generateBotName(List<String> takenNames){
        String name = "";
        final int MAX_BOTS = 10000; //TODO
        for(int i = 1; i < MAX_BOTS; i++){
            name = "BOT_" + i;
            if(!takenNames.contains(name)){
                return name;
            }
        }
//        printState();
        throw new RuntimeException();
    }

    private void sleep(int millis){
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }



    private void printTitle() {
        System.out.println();
        System.out.println("------------------------------------------");
        System.out.println("----------- Multiplayer_Server -----------");
        System.out.println("------------------------------------------");
        System.out.println();
    }

    @Override
    public void gameFinished(GameObject game) {
        hostGameMap.remove(game.getHostName());
//        for(PlayerSocket socket : game.playerSockets){
//            if(socket.isRemote()){
//                RemoteSocket remoteSocket = (RemoteSocket) socket;
//                final boolean isLoggedIn = true;
//                new Thread(
//                        new TCP_ServerPlayerThread(this, isLoggedIn, remoteSocket)).start();
//            }else{
//                //Remote all data for the bot. It's job is done.
//                onlinePlayers.remove(socket.getName());
//                nameSocketMap.remove(socket.getName());
//            }
//        }
//        broadcastOnlineInfo();
        events.publish(new ServerEvent.GameFinished(game));
        printState();
    }

    @Override
    public void gameCrashed(GameObject game) {
//        try{
//            for(PlayerSocket socket : game.playerSockets){
//                if(socket.hasDisconnected()){
//                    removePlayer(socket.getName());
//                }else if(socket.isRemote()){
//                    socket.sendMessage(new Msg(ServerMsg.GAME_CRASHED));
//                }else{ //BOT
//                    removePlayer(socket.getName());
//                }
//            }
//            hostGameMap.remove(game.getHostName());
//
//        }catch(IOException e){
//            e.printStackTrace();
//        }
        hostGameMap.remove(game.getHostName());
        events.publish(new ServerEvent.GameCrashed(game));
        printState();
    }

    public void printState(){
        System.out.println();
        System.out.println(" --------------------------------------------------------");
        System.out.print(getStateString());
        System.out.println();
    }

    private String getStateString(){
        StringBuilder sb = new StringBuilder();
        sb.append("Players: " + onlinePlayers + "\n");
//        if(nameSocketMap.isEmpty()){
//            sb.append("Sockets: {}\n");
//        }else{
//            sb.append("Sockets:\n");
//            appendMapToString(sb, nameSocketMap);
//        }

        if(hostLobbyMap.isEmpty()){
            sb.append("Lobbies: {}\n");
        }else{
            sb.append("Lobbies:\n");
            appendMapToString(sb, hostLobbyMap);
        }

        if(hostGameMap.isEmpty()){
            sb.append("Games: {}\n");
        }else{
            sb.append("Games:\n");
            appendMapToString(sb, hostGameMap);
        }

        return sb.toString();
    }

    private <K,V> StringBuilder appendMapToString(StringBuilder sb, HashMap<K, V> map){
        for(Map.Entry e : map.entrySet()) {
            sb.append("\t" + e.getKey() + ":  " + e.getValue() + "\n");
        }
        return sb;
    }

    synchronized Lobby getLobbyOfHost(String invitedBy) {
        return hostLobbyMap.get(invitedBy);
    }

    void playerLoggedIn(String player){
        onlinePlayers.add(player);
        printState();
        events.publish(new ServerEvent.LoggedIn(player));
    }

    synchronized void playerLoggedOut(String player){
        onlinePlayers.remove(player);
        hostLobbyMap.remove(player);
        events.publish(new ServerEvent.LoggedOut(player));
    }

    synchronized public void addLobby(String name, Lobby newLobby) {
        hostLobbyMap.put(name, newLobby);
        events.publish(new ServerEvent.CreatedLobby(name));
    }

//    synchronized void addPlayer(String name) throws IOException {
//        onlinePlayers.add(name);
////        nameSocketMap.put(name, socket);
//        broadcastOnlineInfo();
//    }

    synchronized List<String> playerNames(){
        return new ArrayList<String>(onlinePlayers);
    }

    synchronized void removeLobby(String host) {
        hostLobbyMap.remove(host);
    }

    synchronized boolean validPlayerName(String name) {
        return !onlinePlayers.contains(name) && name.length() > 0;
    }

//    synchronized PlayerSocket getSocket(String playerName) {
//        return nameSocketMap.get(playerName);
//    }

    synchronized boolean containsPlayer(String name) {
        return onlinePlayers.contains(name);
    }

    public void inviteToLobby(String name, Lobby lobby, String invitedName) {
        lobby.addPlayer(LobbyPlayer.pendingHuman(invitedName));
        events.publish(new ServerEvent.InvitedToLobby(invitedName, lobby));
        printState();
    }

//    //Not sent to bots
//    public static void broadcastLobbyState(Lobby lobby) throws IOException {
//        broadcastInLobby(lobby, new MsgLobbyState(lobby));
//    }
//
//    //Not sent to bots
//    public static void broadcastInLobby(Lobby lobby, Msg<ServerMsg> msg) throws IOException {
//        for(String playerInLobby : lobby.sortedNames()){
//            if(lobby.isConnected(playerInLobby) && lobby.isHuman(playerInLobby)){
//                getSocket(playerInLobby).sendMessage(msg);
//            }
//        }
//    }

    public void acceptInvite(String invited, Lobby lobby) {
        lobby.setConnected(invited);
        events.publish(new ServerEvent.JoinedLobby(invited, lobby));
        printState();
    }

    public void declineInvite(String name, Lobby lobby) {
        lobby.removePlayer(name);
        events.publish(new ServerEvent.DeclinedInvite(name, lobby));
        printState();
    }

    public void addBotToLobby(Lobby lobby) {
        String botName = generateBotName(lobby.sortedNames()); //TODO should be handled better
//        botSocket.joinLobby(thisSocket.getLobby());
        lobby.addPlayer(LobbyPlayer.bot(botName));
        events.publish(new ServerEvent.JoinedLobby(botName, lobby));
        printState();
    }

    public void kickFromLobby(String kicker, String kicked) {
        events.publish(new ServerEvent.KickedFromLobby(kicker, kicked));
    }

    public void leaveLobby(String name, Lobby lobby) {

        boolean isHost = lobby.getHost().equals(name);
        lobby.removePlayer(name);
        if(isHost){
            ArrayList<LobbyPlayer> otherHumansInLobby = lobby.getAllHumans();
            if(otherHumansInLobby.isEmpty()){
//                for(LobbyPlayer bot : lobby.getAllBots()){
//                    server.removePlayer(bot.name);
//                }
                removeLobby(name); //TODO
            }else{
                String newHost = otherHumansInLobby.get(0).name;
                lobby.setNewHost(newHost);
            }
        }

        printState();
    }

    public void startGameFromLobby(Lobby lobby) {
        events.publish(new ServerEvent.LobbyGameStarting(lobby));
//        broadcastInLobby(lobby, new MsgGameIsStarting(lobby.numCols, lobby.numRows, lobby.sortedNames().toArray(new String[0])));
        createGameHostedBy(lobby.getHost(), lobby.size(), lobby.numCols, lobby.numRows);
        for(LobbyPlayer bot : lobby.getAllBots()){
            joinGameHostedBy(lobby.getHost(), bot.name);
        }
        printState();
    }

    public void lobbyDimensionsChanged(Lobby lobby) {
        events.publish(new ServerEvent.LobbyDimensionsChanged(lobby));
    }
}
