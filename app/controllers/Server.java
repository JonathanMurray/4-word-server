package controllers;

import fourword_shared.messages.*;
import fourword_shared.model.*;
import play.libs.F;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by jonathan on 2015-07-08.
 */
public class Server implements ServerGameBehaviour.GameListener {

    private static final int BETWEEN_TURNS_MILLIS = 1200;

    public final static Server INSTANCE = new Server();
    private final static int MAX_ARCHIVED_EVENTS = 100;
    private F.ArchivedEventStream<ServerEvent> events = new F.ArchivedEventStream(MAX_ARCHIVED_EVENTS);

    private HashMap<String, UserId> lowerCaseNameIdMap = new HashMap<String, UserId>();
    private HashMap<UserId, Player> humanPlayers = new HashMap<UserId, Player>();
    private List<Lobby> lobbies = new ArrayList<Lobby>();
    private List<GameObject> games = new ArrayList<GameObject>();

    F.EventStream<ServerEvent> stream(){
        return events.eventStream();
    }



    GameObject createGameHostedBy(UserId hostId, int numPlayers, GameSettings gameSettings){
        GameObject newGame = new GameObject(numPlayers, hostId, gameSettings);
        humanPlayers.get(hostId).hostedGame = newGame;
        games.add(newGame);
        printState();
        return newGame;
    }

    List<String> createGameAndFillWithBots(UserId hostId, int numPlayers, GameSettings gameSettings){
        Player host = humanPlayers.get(hostId);
        GameObject game = createGameHostedBy(hostId, numPlayers, gameSettings);
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
        UserId hostId = lowerCaseNameIdMap.get(hostName.toLowerCase());
        Player host = humanPlayers.get(hostId);
        GameObject game = host.hostedGame;
        Player player = humanPlayers.get(playerId);
        game.addHuman(playerId, player.name);
        player.memberOfLobby = null;
        player.playingGame = game;
        if(game.isReadyToStart()){
            startGame(game);
        }
        printState();
    }

    private void startGame(GameObject game){
        publish(new ServerEvent.GameHasStarted(game));
        if(game.preplacedRandom()){
            GridModel someGrid = game.grids.values().iterator().next();
            int numCells = someGrid.getNumCols() * someGrid.getNumRows();
            int numPreplaced = numPreplaced(numCells);
            for(int i = 0; i < numPreplaced; i++){
                Cell randomFreeCell = someGrid.getRandomFreeCell();
                char randomLetter = Util.randomLetter();
                game.setLetter(randomFreeCell, randomLetter);
                publish(new ServerEvent.GameServerSetLetter(game, randomFreeCell, randomLetter));
            }
        }
        game.runAI();
        delayedPublish(BETWEEN_TURNS_MILLIS, new ServerEvent.GamePlayersTurn(game, game.getActivePlayer(), game.getActivePlayerName()));

    }

    private int numPreplaced(int gridNumCells){
        if(gridNumCells < 8){
            return 1;
        }else if(gridNumCells < 15){
            return 2;
        }else if(gridNumCells < 24){
            return 3;
        }else{
            return 4;
        }
    }

    void joinGameBot(BotId botId, String hostName){
        UserId hostId = lowerCaseNameIdMap.get(hostName.toLowerCase());
        Player host = humanPlayers.get(hostId);
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
        Player host = humanPlayers.get(hostId);
        host.hostedGame = null;
        for(PlayerId playerId : game.players){
            if(playerId instanceof UserId){
                Player player = humanPlayers.get(playerId);
                player.playingGame = null;
            }
        }
        games.remove(game);
        publish(new ServerEvent.GameFinished(game));
        printState();
    }

//    @Override
//    public void gameCrashed(GameObject game) {
//        UserId hostId = game.getHost();
//        Player host = humanPlayers.get(hostId);
//        host.hostedGame = null;
//        for(PlayerId playerId : game.players){
//            if(playerId instanceof UserId){
//                Player player = humanPlayers.get(playerId);
//                player.playingGame = null;
//            }
//        }
//        games.remove(game);
//        publish(new ServerEvent.GameCrashed(game));
//        printState();
//    }

    public Msg<ServerMsg> tryLogin(UserId userId, String loginName) {
        boolean isNameTaken = lowerCaseNameIdMap.containsKey(loginName.toLowerCase());
        if(isNameTaken){
            return new Msg.No("That name is already in use!");
        }
        final int minLength = 3;
        final int maxLength = 10;
        if(loginName.length() < minLength || loginName.length() > maxLength){
            return new Msg.No("Your name has to be " + minLength + " to " + maxLength + " characters long!");
        }
        for(char c : loginName.toCharArray()){
            if(!Character.isLetter(c)){
                return new Msg.No("Your name must only contain letters (no weird symbols!)");
            }
        }
        playerLoggedIn(userId, loginName);
        return new Msg.Ok();
    }

    void playerLoggedIn(UserId userId, String playerName){
        Player player = new Player();
        player.name = playerName;
        assertNotNull(userId, playerName);
        humanPlayers.put(userId, player);
        lowerCaseNameIdMap.put(playerName.toLowerCase(), userId);
        publish(new ServerEvent.LoggedIn(playerName));
        printState();
    }

    synchronized void safePlayerLoggedOut(UserId userId){
        assertNotNull(userId);
        if(humanPlayers.containsKey(userId)){
            Player player = humanPlayers.get(userId);
            if(player.memberOfLobby != null){
                leaveLobby(userId);
            }
            if(player.playingGame != null){
                leaveGame(userId);
            }
            lowerCaseNameIdMap.remove(player.name.toLowerCase());
            humanPlayers.remove(userId);
            publish(new ServerEvent.LoggedOut(player.name));

            printState();
        }
    }



    synchronized public void createLobby(UserId host) {
        Player player = humanPlayers.get(host);
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
        Player inviter = humanPlayers.get(inviterId);

        if(inviter.name.equals(invitedName)){
            return new Msg.No("You can't invite yourself!");
        }else if(!lowerCaseNameIdMap.containsKey(invitedName.toLowerCase())){
            return new Msg.No("Can't find that player!");
        }else {
            UserId invitedId = lowerCaseNameIdMap.get(invitedName.toLowerCase());
            Player invited = humanPlayers.get(invitedId);
            Lobby lobby = inviter.memberOfLobby;
            if(lobby.size() == Lobby.MAX_PLAYERS){
                return new Msg.No("Can't invite more players to this lobby!");
            }else if (invited.invitedToLobby != null) {
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
        String inviterName = humanPlayers.get(host).name;
        Lobby lobby = humanPlayers.get(host).hostedLobby;
        lobby.addPlayer(LobbyPlayer.pendingHuman(invitedName));
        UserId invitedId = lowerCaseNameIdMap.get(invitedName.toLowerCase());
        humanPlayers.get(invitedId).invitedToLobby = lobby;
        publish(new ServerEvent.InvitedToLobby(inviterName, invitedId, invitedName, lobby));
        printState();
    }

    public void acceptInvite(UserId invitedId) {
        Player invited = humanPlayers.get(invitedId);
        Lobby lobby = invited.invitedToLobby;
        lobby.setConnected(invited.name);
        invited.invitedToLobby = null;
        invited.memberOfLobby = lobby;
        publish(ServerEvent.JoinedLobby.human(invitedId, invited.name, lobby));
        printState();
    }

    public void declineInvite(UserId invitedId) {
        Player invited = humanPlayers.get(invitedId);
        Lobby lobby = invited.invitedToLobby;
        lobby.removePlayer(invited.name);
        invited.invitedToLobby = null;
        publish(new ServerEvent.DeclinedInvite(invited.name, lobby));
        printState();
    }

    public void addBotToLobby(UserId host) {
        Lobby lobby = humanPlayers.get(host).hostedLobby;
        String botName = generateBotName(lobby.sortedNames()); //TODO should be handled better
        LobbyPlayer bot = LobbyPlayer.bot(botName);
        lobby.addPlayer(bot);
        publish(ServerEvent.JoinedLobby.bot(botName, lobby));
        printState();
    }

    public void kickFromLobby(UserId kicker, String kickedName) {
        Lobby lobby = humanPlayers.get(kicker).hostedLobby;
        if(lobby.containsPlayer(kickedName)){
            boolean kickedIsHuman = lobby.isHuman(kickedName);
            lobby.removePlayer(kickedName);
            String kickerName = humanPlayers.get(kicker).name;
            if(kickedIsHuman){
                UserId kickedId = lowerCaseNameIdMap.get(kickedName.toLowerCase());
                Player kicked = humanPlayers.get(kickedId);
                kicked.memberOfLobby = null;
                kicked.invitedToLobby = null; //in the case that the player was kicked even before accepting the invite
                publish(new ServerEvent.KickedFromLobby(kickerName, lobby, kickedId, kickedName));
            }else{
                publish(new ServerEvent.KickedFromLobby(kickerName, lobby, null, kickedName));
            }
            printState();
        }else{
            System.out.println("Trying to kick " + kickedName + " but that player is not in lobby");
        }
    }

    public boolean isLoggedInHuman(PlayerId playerId){
        return humanPlayers.containsKey(playerId);
    }

    public void leaveLobby(UserId leaverId) {
        Player leaver = humanPlayers.get(leaverId);
        Lobby lobby = leaver.memberOfLobby;
        boolean isHost = leaver.hostedLobby != null;
        leaver.memberOfLobby = null;
        lobby.removePlayer(leaver.name);
        publish(new ServerEvent.LeftLobby(leaverId, leaver.name, lobby));
        if(isHost){
            leaver.hostedLobby = null;
            ArrayList<LobbyPlayer> otherHumansInLobby = lobby.getAllHumans();
            if(otherHumansInLobby.isEmpty()){
                removeEmptyLobby(lobby);
            }else{
                String newHostName = otherHumansInLobby.get(0).name;
                UserId newHostId = lowerCaseNameIdMap.get(newHostName.toLowerCase());
                Player newHost = humanPlayers.get(newHostId);
                newHost.hostedLobby = lobby;
                lobby.setNewHost( newHostName);
                publish(new ServerEvent.LobbyNewHost(lobby, newHostName));
            }
        }
        printState();
    }

    public void leaveGame(UserId userId) {
        Player leaver = humanPlayers.get(userId);
        GameObject game = leaver.playingGame;
        leaver.hostedGame = null;
        for(PlayerId p : game.players){
            if(p instanceof UserId){
                Player playerInGame = humanPlayers.get(p);
                playerInGame.playingGame = null;
            }
        }
        games.remove(game);
        printState();
        publish(new ServerEvent.GameEnded(game, leaver.name, userId));
    }

    public List<PlayerInfo> getPlayerInfoForAllInLobby(Lobby lobby){
        ArrayList<PlayerInfo> infos = new ArrayList<PlayerInfo>();
        for(LobbyPlayer human : lobby.getAllHumans()){
            UserId playerId = lowerCaseNameIdMap.get(human.name.toLowerCase());
            Player player = humanPlayers.get(playerId);
            infos.add(player.getPlayerInfo());
        }
        return infos;
    }

    public List<PlayerInfo> getPlayerInfoForAllInGame(GameObject game) {
        ArrayList<PlayerInfo> infos = new ArrayList<PlayerInfo>();
        for(PlayerId playerId : game.players){
            if(playerId instanceof UserId){
                Player player = humanPlayers.get(playerId);
                infos.add(player.getPlayerInfo());
            }
        }
        return infos;
    }



    public PlayerInfo getPlayerInfo(UserId player){
        return humanPlayers.get(player).getPlayerInfo();
    }


    public boolean isPlayerInLobby(UserId player, Lobby lobby){
        return lobby.containsPlayer(humanPlayers.get(player).name);
    }

    public boolean isPlayerInGame(UserId player, GameObject game){
        return game.containsPlayer(player);
//        return game.equals(players.get(player).playingGame);
    }

    public boolean isPlayerInMenuOrLobbyHost(UserId playerId){
        return isPlayerInMenu(playerId) || isPlayerHostingLobby(playerId);
    }

    public boolean isPlayerInMenu(UserId playerId){
        Player player = humanPlayers.get(playerId);
        return player.memberOfLobby == null && player.playingGame == null;
    }

    public boolean isPlayerHostingLobby(UserId playerId){
        Player player = humanPlayers.get(playerId);
        return player.hostedLobby != null;
    }

    public boolean isUserPlayer(UserId userId, String playerName){
        return humanPlayers.get(userId).name.equals(playerName);
    }

    public Msg<ServerMsg> tryStartGameFromLobby(UserId hostId){
        Player host = humanPlayers.get(hostId);
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
        Player host = humanPlayers.get(hostId);
        Lobby lobby = host.hostedLobby;
        host.hostedLobby = null;
        for(LobbyPlayer human : lobby.getAllHumans()){
            UserId playerId = lowerCaseNameIdMap.get(human.name.toLowerCase());
            Player player = humanPlayers.get(playerId);
            player.memberOfLobby = null;
        }
        removeEmptyLobby(lobby);

        createGameHostedBy(hostId, lobby.size(), lobby.getSettings());
        publish(new ServerEvent.LobbyGameStarting(lobby));
        for(LobbyPlayer bot : lobby.getAllBots()){
            joinGameBot(new BotId(bot.name), host.name);
        }
        printState();
    }

    public void lobbySetVar(UserId hostId, GameSettings.Attribute var, Serializable value){
        Lobby lobby = humanPlayers.get(hostId).hostedLobby;
        lobby.getSettings().setAttribute(var, value);
        publish(new ServerEvent.LobbySetVar(lobby, var, value));
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
        Player player = humanPlayers.get(userId);
        assertIsPlayingGame(userId, player);
        GameObject game = player.playingGame;
        boolean nextTurn = game.placeLetter(userId, cell);
        publish(new ServerEvent.PlacedLetter(game, player.name, cell));
        if(nextTurn){
            if(game.isFinished()){
                gameFinished(game);
            }else{
                delayedPublish(BETWEEN_TURNS_MILLIS, new ServerEvent.GamePlayersTurn(game, game.getActivePlayer(), game.getActivePlayerName()));
            }
        }

    }

    public void playerPickedAndPlacedLetter(UserId userId, char letter, Cell cell) {
        Player player = humanPlayers.get(userId);
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
        for(Player p : humanPlayers.values()){
            onlinePlayers.add(p.getPlayerInfo());
        }
        return onlinePlayers;
    }

    public boolean isUserOnline(UserId user){
        return humanPlayers.containsKey(user);
    }


    //TODO Be careful when publishing events that the state has been correctly been set up
    //according to what is said in the published message.
    //Don't publish GAME_HAS_STARTED if not all players have had their playingGame-var set
    //to the game, and the gameobject has been setup fully.





    //---------------------------------------------------------------------------

    public void printState(){
        System.out.println();
        System.out.println(" --------------------------------------------------------");
        System.out.print(getStateString());
        System.out.println();
    }

    private String getStateString(){
        StringBuilder sb = new StringBuilder();
        sb.append("Players: " + humanPlayers + "\n");

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
