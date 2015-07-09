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
    private final static int MAX_ARCHIVED_EVENTS = 100;

    private F.ArchivedEventStream<ServerEvent> events = new F.ArchivedEventStream(MAX_ARCHIVED_EVENTS);

    F.EventStream<ServerEvent> stream(){
        return events.eventStream();
    }



    private HashMap<String, UserId> lowerCaseNameIdMap = new HashMap<String, UserId>();
//    private HashMap<UserId, String> onlinePlayers = new HashMap<UserId, String>();
//    private HashMap<UserId, Lobby> hostLobbyMap = new HashMap<UserId, Lobby>();
//    private HashMap<UserId, Lobby> invitedToLobbyMap = new HashMap<UserId, Lobby>();
//    private HashMap<UserId, GameObject> hostGameMap = new HashMap<UserId, GameObject>();

    private HashMap<UserId, Player> players = new HashMap<UserId, Player>();
    private List<Lobby> lobbies = new ArrayList<Lobby>();
    private List<GameObject> games = new ArrayList<GameObject>();



    void createGameHostedBy(UserId host, int numPlayers, int numCols, int numRows){
        GameObject newGame = new GameObject(numPlayers, host, numCols, numRows);
        players.get(host).hostedGame = newGame;
        games.add(newGame);
        printState();
    }

    List<String> createGameAndFillWithBots(UserId host, int numPlayers, int numCols, int numRows){
        createGameHostedBy(host, numPlayers, numCols, numRows);
        int numBots = numPlayers - 1;
        List<String> names = new ArrayList<String>();
        names.add(players.get(host).name);
        for(int i = 0; i < numBots; i++){
            String botName = generateBotName(names);
            names.add(botName);
            joinGameHostedBy(host, botName);
        }
        printState();
        return names;
    }

    void joinGame(UserId playerId, String hostName){
        UserId hostId = lowerCaseNameIdMap.get(hostName);
        Player host = players.get(hostId);
        GameObject game = host.hostedGame;
        game.join(playerId);
        Player player = players.get(playerId);
        if(game.isReadyToStart()){
//            ServerGameBehaviour gameThread = new ServerGameBehaviour(this, game);
//            new Thread(gameThread).start();
//            return true;
            player.memberOfLobby = null;
        }
    }

    boolean joinGameHostedBy(UserId host, String joiningPlayer){
        GameObject game = players.get(host).hostedGame;
//        game.join(joiningPlayer);
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

    @Override
    public void gameFinished(GameObject game) {
        UserId hostId = game.getHost();
        Player host = players.get(hostId);
        host.hostedGame = null;
        games.remove(game);
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
        UserId hostId = game.getHost();
        Player host = players.get(hostId);
        host.hostedGame = null;
        games.remove(game);
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
        sb.append("Players: " + players + "\n");
//        if(nameSocketMap.isEmpty()){
//            sb.append("Sockets: {}\n");
//        }else{
//            sb.append("Sockets:\n");
//            appendMapToString(sb, nameSocketMap);
//        }

        if(lobbies.isEmpty()){
            sb.append("Lobbies: {}\n");
        }else{
            sb.append("Lobbies:\n");
            appendToString(sb, lobbies);
        }

        if(games.isEmpty()){
            sb.append("Games: {}\n");
        }else{
            sb.append("Games:\n");
            appendToString(sb, games);
        }

        return sb.toString();
    }

    private <K,V> StringBuilder appendToString(StringBuilder sb, HashMap<K, V> map){
        for(Map.Entry e : map.entrySet()) {
            sb.append("\t" + e.getKey() + ":  " + e.getValue() + "\n");
        }
        return sb;
    }

    private <T> StringBuilder appendToString(StringBuilder sb, List<T> list){
        for(T t : list){
            sb.append("\t" + t + "\n");
        }
        return sb;
    }

    synchronized Lobby getLobbyOfHost(UserId host) {
        return players.get(host).hostedLobby;
    }


    public Msg<ServerMsg> tryLogin(UserId userId, String loginName) {
        boolean validName = ! lowerCaseNameIdMap.containsKey(loginName.toLowerCase());
        if(validName){
            playerLoggedIn(userId, loginName);
            return new Msg(ServerMsg.OK);
        }else{
            return new MsgText(ServerMsg.NO, "That name is already in use!");
        }
    }

    void playerLoggedIn(UserId userId, String playerName){
        Player player = new Player();
        player.name = playerName;
        assertNotNull(userId, playerName);
        players.put(userId, player);
        lowerCaseNameIdMap.put(playerName.toLowerCase(), userId);
        events.publish(new ServerEvent.LoggedIn(playerName));
        printState();
    }

    synchronized void playerLoggedOut(UserId player){
        assertNotNull(player);
        String playerName = players.get(player).name;
        events.publish(new ServerEvent.LoggedOut(playerName));
        lowerCaseNameIdMap.remove(playerName.toLowerCase());
        players.remove(player);
        //TODO handle case when the player was hosting a lobby
        printState();
    }

    synchronized public void createLobby(UserId host) {
        Player player = players.get(host);
        String hostName = player.name;
        Lobby newLobby = new Lobby( hostName);
        lobbies.add(newLobby);
        player.hostedLobby = newLobby;
        player.memberOfLobby = newLobby;
        assertNotNull(host, newLobby);
        events.publish(new ServerEvent.CreatedLobby(hostName));
        printState();
    }

    private void assertNotNull(Object... args){
        boolean someNull = false;
        for(Object arg : args){
            if(arg == null){
                someNull = true;
            }
        }
        if(someNull){
            String s = "[";
            for(Object arg : args){
                s += arg + ", ";
            }
            s += "]";
            throw new IllegalArgumentException("Some args are null: " + s);
        }
    }

    synchronized void removeLobby(UserId host) {
        Lobby lobby = players.get(host).hostedLobby;
        lobbies.remove(lobby);
        players.get(host).hostedLobby = null;
        //TODO handle players who are "member of lobby"
        printState();
    }

    public Msg<ServerMsg> tryInviteToLobby(UserId inviterId, String invitedName) {
        Player inviter = players.get(inviterId);

        if(inviter.name.equals(invitedName)){
            return new MsgText(ServerMsg.NO, "You can't invite yourself!");
        }else if(!lowerCaseNameIdMap.containsKey(invitedName.toLowerCase())){
            return new MsgText(ServerMsg.NO, "Can't find that player!");
        }else{
            UserId invitedId = lowerCaseNameIdMap.get(invitedName.toLowerCase());
            Player invited = players.get(invitedId);
            if(invited.invitedToLobby != null) { //TODO Handle player is already invited
                return new MsgText(ServerMsg.NO, "That player already has a pending invite!");
            }else if(invited.memberOfLobby != null) {//TODO Handle player is in obby
                return (new MsgText(ServerMsg.NO, "That player is already in a lobby!"));
            }else{
                inviteToLobby(inviterId, invitedName);
                return (new Msg(ServerMsg.OK));
            }
        }
    }

    public void inviteToLobby(UserId host, String invitedName) {
        Lobby lobby = players.get(host).hostedLobby;
        lobby.addPlayer(LobbyPlayer.pendingHuman(invitedName));
        UserId invited = lowerCaseNameIdMap.get(invitedName);
        players.get(invited).invitedToLobby = lobby;
        events.publish(new ServerEvent.InvitedToLobby(invitedName, lobby));
        printState();
    }

    public void acceptInvite(UserId invitedId) {
        Player invited = players.get(invitedId);
        Lobby lobby = invited.invitedToLobby;
        lobby.setConnected(invited.name);
        invited.invitedToLobby = null;
        invited.memberOfLobby = lobby;
        events.publish(new ServerEvent.JoinedLobby(invited.name, lobby));
        printState();
    }

    public void declineInvite(UserId invitedId) {
        Player invited = players.get(invitedId);
        Lobby lobby = invited.invitedToLobby;
        lobby.removePlayer(invited.name);
        invited.invitedToLobby = null;
        events.publish(new ServerEvent.DeclinedInvite(invited.name, lobby));
        printState();
    }

    public void addBotToLobby(UserId host) {
        Lobby lobby = players.get(host).hostedLobby;
        String botName = generateBotName(lobby.sortedNames()); //TODO should be handled better
//        botSocket.joinLobby(thisSocket.getLobby());
        lobby.addPlayer(LobbyPlayer.bot(botName));
        events.publish(new ServerEvent.JoinedLobby(botName, lobby));
        printState();
    }

    public void kickFromLobby(UserId kicker, String kickedName) {
        Lobby lobby = players.get(kicker).hostedLobby;
        lobby.removePlayer(kickedName);
        String kickerName = players.get(kicker).name;
        UserId kickedId = lowerCaseNameIdMap.get(kickedName.toLowerCase());
        Player kicked = players.get(kickedId);
        kicked.memberOfLobby = null;
        events.publish(new ServerEvent.KickedFromLobby(kickerName, lobby, kickedId));
        printState();
    }

    public void leaveLobby(UserId leaverId) {
        Player leaver = players.get(leaverId);
        boolean isHost = leaver.hostedLobby != null;
        Lobby lobby = leaver.memberOfLobby;
        lobby.removePlayer(leaver.name);
        if(isHost){
            ArrayList<LobbyPlayer> otherHumansInLobby = lobby.getAllHumans();
            if(otherHumansInLobby.isEmpty()){
//                for(LobbyPlayer bot : lobby.getAllBots()){
//                    server.removePlayer(bot.name);
//                }
                removeLobby(leaverId); //TODO
            }else{
                String newHostName = otherHumansInLobby.get(0).name;
                UserId newHost = lowerCaseNameIdMap.get(newHostName.toLowerCase());
                lobby.setNewHost( newHostName);
            }
        }

        printState();
    }

    public boolean isPlayerInLobby(UserId player, Lobby lobby){
        return lobby.containsPlayer(players.get(player).name);
    }

    public Msg<ServerMsg> tryStartGameFromLobby(UserId hostId){
        Player host = players.get(hostId);
        Lobby lobby = host.hostedLobby;
        boolean enoughPlayers = lobby.size() > 1;
        if(enoughPlayers){
            startGameFromLobby(hostId);
            return null;
        }else{
            return new MsgText(ServerMsg.NO, "Not enough playerNames!");
        }
    }

    public void startGameFromLobby(UserId hostId) {
        Player host = players.get(hostId);
        Lobby lobby = host.hostedLobby;
        host.hostedLobby = null;
        events.publish(new ServerEvent.LobbyGameStarting(lobby));
//        broadcastInLobby(lobby, new MsgGameIsStarting(lobby.numCols, lobby.numRows, lobby.sortedNames().toArray(new String[0])));

        createGameHostedBy(hostId, lobby.size(), lobby.numCols, lobby.numRows);
        for(LobbyPlayer bot : lobby.getAllBots()){
            joinGameHostedBy(hostId, bot.name);
        }
        printState();
    }

    public void changeLobbyDimensions(UserId hostId, int numCols, int numRows) {
        Lobby lobby = players.get(hostId).hostedLobby;
        lobby.numCols = numCols;
        lobby.numRows = numRows;
        events.publish(new ServerEvent.LobbyDimensionsChanged(lobby));
        printState();
    }




    private class Player{
        String name;
        Lobby hostedLobby;
        Lobby invitedToLobby;
        Lobby memberOfLobby;
        GameObject hostedGame;

        public String toString(){
            return name;
        }
    }
}
