package controllers;

import fourword_shared.messages.*;
import fourword_shared.model.Cell;
import fourword_shared.model.Lobby;
import fourword_shared.model.LobbyPlayer;
import fourword_shared.model.PlayerInfo;
import play.libs.F;

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

    private HashMap<String, UserId> lowerCaseNameIdMap = new HashMap<String, UserId>();
    private HashMap<UserId, Player> players = new HashMap<UserId, Player>();
    private List<Lobby> lobbies = new ArrayList<Lobby>();
    private List<GameObject> games = new ArrayList<GameObject>();

    F.EventStream<ServerEvent> stream(){
        return events.eventStream();
    }



    GameObject createGameHostedBy(UserId hostId, int numPlayers, int numCols, int numRows){
        GameObject newGame = new GameObject(numPlayers, hostId, numCols, numRows);
        players.get(hostId).hostedGame = newGame;
        games.add(newGame);
        printState();
        return newGame;
    }

    List<String> createGameAndFillWithBots(UserId hostId, int numPlayers, int numCols, int numRows){
        Player host = players.get(hostId);
        GameObject game = createGameHostedBy(hostId, numPlayers, numCols, numRows);
        int numBots = numPlayers - 1;
        List<String> names = new ArrayList<String>();
        names.add(host.name);
        for(int i = 0; i < numBots; i++){
            String botName = generateBotName(names);
            names.add(botName);
            game.addBot(new BotId(botName));
//            host.hostedGame.
        }

        printState();
        return names;
    }

    void joinGameHuman(UserId playerId, String hostName){
        UserId hostId = lowerCaseNameIdMap.get(hostName);
        Player host = players.get(hostId);
        GameObject game = host.hostedGame;
        Player player = players.get(playerId);
        game.addHuman(playerId, player.name);

        if(game.isReadyToStart()){
//            removeEmptyLobby(player.memberOfLobby); /payer.memberoflobby seems to be null
            game.runAI();
            publish(new ServerEvent.GameHasStarted(game));
            publish(new ServerEvent.GamePlayersTurn(game, game.getActivePlayer(), game.getActivePlayerName()));

        }
        player.memberOfLobby = null;
        player.playingGame = game;
        printState();
    }

    void joinGameBot(BotId botId, String hostName){
        UserId hostId = lowerCaseNameIdMap.get(hostName);
        Player host = players.get(hostId);
        GameObject game = host.hostedGame;
        game.addBot(botId);
        printState();
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
        throw new RuntimeException();
    }

    @Override
    public void gameFinished(GameObject game) {
        UserId hostId = game.getHost();
        Player host = players.get(hostId);
        host.hostedGame = null;
        for(PlayerId playerId : game.players){
            if(playerId instanceof UserId){
                Player player = players.get(playerId);
                player.playingGame = null;
            }
        }
        games.remove(game);
        publish(new ServerEvent.GameFinished(game));
        printState();
    }

    @Override
    public void gameCrashed(GameObject game) {
        UserId hostId = game.getHost();
        Player host = players.get(hostId);
        host.hostedGame = null;
        for(PlayerId playerId : game.players){
            if(playerId instanceof UserId){
                Player player = players.get(playerId);
                player.playingGame = null;
            }
        }
        games.remove(game);
        publish(new ServerEvent.GameCrashed(game));
        printState();
    }

    public Msg<ServerMsg> tryLogin(UserId userId, String loginName) {
        boolean validName = ! lowerCaseNameIdMap.containsKey(loginName.toLowerCase());
        if(validName){
            playerLoggedIn(userId, loginName);
            return new Msg.Ok();
        }else{
            return new Msg.No("That name is already in use!");
        }
    }

    void playerLoggedIn(UserId userId, String playerName){
        Player player = new Player();
        player.name = playerName;
        assertNotNull(userId, playerName);
        players.put(userId, player);
        lowerCaseNameIdMap.put(playerName.toLowerCase(), userId);
        publish(new ServerEvent.LoggedIn(playerName));
        printState();
    }

    synchronized void safePlayerLoggedOut(UserId userId){
        assertNotNull(userId);
        if(players.containsKey(userId)){
            Player player = players.get(userId);
            publish(new ServerEvent.LoggedOut(player.name));
            lowerCaseNameIdMap.remove(player.name.toLowerCase());
            players.remove(userId);
            if(player.memberOfLobby != null){
                leaveLobby(userId);
            }
            printState();
        }
    }



    synchronized public void createLobby(UserId host) {
        Player player = players.get(host);
        String hostName = player.name;
        Lobby newLobby = new Lobby( hostName);
        lobbies.add(newLobby);
        player.hostedLobby = newLobby;
        player.memberOfLobby = newLobby;
        assertNotNull(host, newLobby);
        publish(new ServerEvent.CreatedLobby(host, hostName));
        printState();
    }



    synchronized void removeEmptyLobby(Lobby lobby) {
        lobbies.remove(lobby);
        publish(new ServerEvent.LobbyRemoved(lobby));
        printState();
    }

    public Msg<ServerMsg> tryInviteToLobby(UserId inviterId, String invitedName) {
        Player inviter = players.get(inviterId);

        if(inviter.name.equals(invitedName)){
            return new Msg.No("You can't invite yourself!");
        }else if(!lowerCaseNameIdMap.containsKey(invitedName.toLowerCase())){
            return new Msg.No("Can't find that player!");
        }else {
            UserId invitedId = lowerCaseNameIdMap.get(invitedName.toLowerCase());
            Player invited = players.get(invitedId);
            if (invited.invitedToLobby != null) {
                return new Msg.No("That player already has a pending invite!");
            } else if (invited.memberOfLobby != null) {
                return (new Msg.No("That player is already in a lobby!"));
            }else if(invited.playingGame != null){
                return new Msg.No("That player is already in a game!");
            }else{
                inviteToLobby(inviterId, invitedName);
                return (new Msg.Ok());
            }
        }
    }

    public void inviteToLobby(UserId host, String invitedName) {
        String inviterName = players.get(host).name;
        Lobby lobby = players.get(host).hostedLobby;
        lobby.addPlayer(LobbyPlayer.pendingHuman(invitedName));
        UserId invitedId = lowerCaseNameIdMap.get(invitedName);
        players.get(invitedId).invitedToLobby = lobby;
        publish(new ServerEvent.InvitedToLobby(inviterName, invitedId, lobby));
        printState();
    }

    public void acceptInvite(UserId invitedId) {
        Player invited = players.get(invitedId);
        Lobby lobby = invited.invitedToLobby;
        lobby.setConnected(invited.name);
        invited.invitedToLobby = null;
        invited.memberOfLobby = lobby;
        publish(new ServerEvent.JoinedLobby(invited.name, lobby));
        printState();
    }

    public void declineInvite(UserId invitedId) {
        Player invited = players.get(invitedId);
        Lobby lobby = invited.invitedToLobby;
        lobby.removePlayer(invited.name);
        invited.invitedToLobby = null;
        publish(new ServerEvent.DeclinedInvite(invited.name, lobby));
        printState();
    }

    public void addBotToLobby(UserId host) {
        Lobby lobby = players.get(host).hostedLobby;
        String botName = generateBotName(lobby.sortedNames()); //TODO should be handled better
        lobby.addPlayer(LobbyPlayer.bot(botName));
        publish(new ServerEvent.JoinedLobby(botName, lobby));
        printState();
    }

    public void kickFromLobby(UserId kicker, String kickedName) {
        Lobby lobby = players.get(kicker).hostedLobby;
        boolean kickedIsHuman = lobby.isHuman(kickedName);
        lobby.removePlayer(kickedName);
        if(kickedIsHuman){
            String kickerName = players.get(kicker).name;
            UserId kickedId = lowerCaseNameIdMap.get(kickedName.toLowerCase());
            Player kicked = players.get(kickedId);
            kicked.memberOfLobby = null;
            kicked.invitedToLobby = null; //in the case that the player was kicked even before accepting the invite
            publish(new ServerEvent.KickedFromLobby(kickerName, lobby, kickedId));
        }
        printState();
    }

    public void leaveLobby(UserId leaverId) {
        Player leaver = players.get(leaverId);
        Lobby lobby = leaver.memberOfLobby;
        boolean isHost = leaver.hostedLobby != null;
        leaver.memberOfLobby = null;
        lobby.removePlayer(leaver.name);
        publish(new ServerEvent.LeftLobby(leaver.name, lobby));
        if(isHost){
            leaver.hostedLobby = null;
            ArrayList<LobbyPlayer> otherHumansInLobby = lobby.getAllHumans();
            if(otherHumansInLobby.isEmpty()){
                removeEmptyLobby(lobby);
            }else{
                String newHostName = otherHumansInLobby.get(0).name;
                UserId newHostId = lowerCaseNameIdMap.get(newHostName.toLowerCase());
                Player newHost = players.get(newHostId);
                newHost.hostedLobby = lobby;
                lobby.setNewHost( newHostName);
                publish(new ServerEvent.LobbyNewHost(lobby));
            }
        }
        printState();
    }

    public List<PlayerInfo> getPlayerInfoForAllInLobby(Lobby lobby){
        ArrayList<PlayerInfo> infos = new ArrayList<PlayerInfo>();
        for(LobbyPlayer human : lobby.getAllHumans()){
            UserId playerId = lowerCaseNameIdMap.get(human.name);
            Player player = players.get(playerId);
            infos.add(player.getPlayerInfo());
        }
        return infos;
    }

    public List<PlayerInfo> getPlayerInfoForAllInGame(GameObject game) {
        ArrayList<PlayerInfo> infos = new ArrayList<PlayerInfo>();
        for(PlayerId playerId : game.players){
            if(playerId instanceof UserId){
                Player player = players.get(playerId);
                infos.add(player.getPlayerInfo());
            }
        }
        return infos;
    }


    public boolean isPlayerInLobby(UserId player, Lobby lobby){
        return lobby.containsPlayer(players.get(player).name);
    }

    public boolean isPlayerInGame(UserId player, GameObject game){
        return game.containsPlayer(player);
//        return game.equals(players.get(player).playingGame);
    }

    public boolean isPlayerInMenu(UserId playerId){
        Player player = players.get(playerId);
        return player.memberOfLobby == null && player.playingGame == null;
    }

    public boolean isUserPlayer(UserId userId, String playerName){
        return players.get(userId).name.equals(playerName);
    }

    public Msg<ServerMsg> tryStartGameFromLobby(UserId hostId){
        Player host = players.get(hostId);
        Lobby lobby = host.hostedLobby;
        boolean enoughPlayers = lobby.size() > 1;
        if(enoughPlayers){
            startGameFromLobby(hostId);
            return null;
        }else{
            return new Msg.No("Not enough playerNames!");
        }
    }

    public void startGameFromLobby(UserId hostId) {
        Player host = players.get(hostId);
        Lobby lobby = host.hostedLobby;
        host.hostedLobby = null;
        for(LobbyPlayer human : lobby.getAllHumans()){
            UserId playerId = lowerCaseNameIdMap.get(human.name.toLowerCase());
            Player player = players.get(playerId);
            player.memberOfLobby = null;
        }
        removeEmptyLobby(lobby);
        publish(new ServerEvent.LobbyGameStarting(lobby));
        createGameHostedBy(hostId, lobby.size(), lobby.numCols, lobby.numRows);
        for(LobbyPlayer bot : lobby.getAllBots()){
            joinGameBot(new BotId(bot.name), host.name);
        }
        printState();
    }

    public void changeLobbyDimensions(UserId hostId, int numCols, int numRows) {
        Lobby lobby = players.get(hostId).hostedLobby;
        lobby.numCols = numCols;
        lobby.numRows = numRows;
        publish(new ServerEvent.LobbyDimensionsChanged(lobby));
        printState();
    }

    public void botPickedAndPlacedLetter(GameObject game, BotId botId, char letter, Cell cell){
        game.pickAndPlaceLetter(botId, letter, cell);
        publish(new ServerEvent.PickedAndPlacedLetter(game, botId.botName, letter, cell));
    }

    public void botPlacedLetter(GameObject game, BotId botId, Cell cell){
        boolean nextTurn = game.placeLetter(botId, cell);
        publish(new ServerEvent.PlacedLetter(game, botId.botName, cell));
        if(nextTurn){
            if(game.isFinished()){
                gameFinished(game);
            }else {
                int BETWEEN_TURNS_MILLIS = 1000;
                delayedPublish(BETWEEN_TURNS_MILLIS, new ServerEvent.GamePlayersTurn(game, game.getActivePlayer(), game.getActivePlayerName()));
            }
        }
    }

    private void delayedPublish(final int millis, final ServerEvent e){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                publish(e);
            }
        }).start();
    }

    public void playerPlacedLetter(UserId userId, Cell cell) {
        Player player = players.get(userId);
        assertIsPlayingGame(userId, player);
        GameObject game = player.playingGame;
        boolean nextTurn = game.placeLetter(userId, cell);
        publish(new ServerEvent.PlacedLetter(game, player.name, cell));
        if(nextTurn){
            if(game.isFinished()){
                gameFinished(game);
            }else{
                publish(new ServerEvent.GamePlayersTurn(game, game.getActivePlayer(), game.getActivePlayerName()));
            }
        }

    }

    public void playerPickedAndPlacedLetter(UserId userId, char letter, Cell cell) {
        Player player = players.get(userId);
        assertIsPlayingGame(userId, player);
        player.playingGame.pickAndPlaceLetter(userId, letter, cell);
        publish(new ServerEvent.PickedAndPlacedLetter(player.playingGame, player.name, letter, cell));
    }

    private void assertIsPlayingGame(UserId userId, Player player){
        if(player.playingGame == null){
            throw new RuntimeException("User " + userId + " is not playing a game!");
        }
    }




    private class Player{
        String name;
        Lobby hostedLobby;
        Lobby invitedToLobby;
        Lobby memberOfLobby;
        GameObject hostedGame;
        GameObject playingGame;

        public String toString(){
            return name
                    + (hostedLobby != null ? "|lobbyHost" : "")
                    + (invitedToLobby != null ? "|invited" : "")
                    + (memberOfLobby != null ? "|inLobby" : "")
                    + (hostedGame != null ? "|gameHost" : "")
                    + (playingGame != null ? "|inGame" : "");

        }

        public PlayerInfo getPlayerInfo(){
            if(memberOfLobby != null) {
                return PlayerInfo.inLobby(name, hostedLobby != null, memberOfLobby.size() - 1);
            }else if(playingGame != null) {
                return PlayerInfo.inGame(name, playingGame.getNumPlayers() - 1);
            }else{
                return PlayerInfo.inMenu(name);
            }
        }
    }

    public List<PlayerInfo> onlinePlayers(){
        ArrayList<PlayerInfo> onlinePlayers = new ArrayList<PlayerInfo>();
        for(Player p : players.values()){
            onlinePlayers.add(p.getPlayerInfo());
        }
        return onlinePlayers;
    }

    public boolean isUserOnline(UserId user){
        return players.containsKey(user);
    }








    //---------------------------------------------------------------------------

    public void printState(){
        System.out.println();
        System.out.println(" --------------------------------------------------------");
        System.out.print(getStateString());
        System.out.println();
    }

    private String getStateString(){
        StringBuilder sb = new StringBuilder();
        sb.append("Players: " + players + "\n");

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

    synchronized private void publish(ServerEvent e){
        events.publish(e);
        System.out.println("Published event: " + e);
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
}
