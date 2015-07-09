package controllers;

import fourword_shared.messages.ClientMsg;
import fourword_shared.messages.Msg;
import fourword_shared.model.Cell;
import fourword_shared.model.GridModel;
import play.libs.F;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

/**
 * Created by jonathan on 2015-06-27.
 */
public class GameObject {

    private static Server server = Server.INSTANCE;



    private enum State{
        PICKING_AND_PLACING,
        PLACING,
        FINISHED;
    }

    private State state = State.PICKING_AND_PLACING;
    private int activePlayerIndex = 0;
    private char pickedLetter;
    private int numWaitingForPlace;
    private int numCellsLeftToFill;

    private final int numPlayers;
    private final UserId host;
    public ArrayList<PlayerId> players = new ArrayList();
    public HashMap<BotId, AI> ais = new HashMap<BotId, AI>();
    public ArrayList<String> playerNames = new ArrayList<String>();
    public HashMap<PlayerId, GridModel> grids = new HashMap<PlayerId, GridModel>();
    private final int numCols;
    private final int numRows;

    private final F.EventStream<ServerEvent> serverEventStream = server.stream();

    public void runAI(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while(state != State.FINISHED){
                        ServerEvent e = serverEventStream.nextEvent().get();
                        switch (e.type){
                            case GAME_PLAYERS_TURN:
                                ServerEvent.GamePlayersTurn playersTurn = (ServerEvent.GamePlayersTurn) e;
                                if(playersTurn.game.equals(GameObject.this)){
                                    if(playersTurn.playerId instanceof BotId){
                                        BotId botId = (BotId) playersTurn.playerId;
                                        AI activeAI = ais.get(botId);
                                        Msg.PickAndPlaceLetter reply = (Msg.PickAndPlaceLetter) activeAI.handleServerMessageAndProduceReply(
                                                new Msg.RequestPickAndPlaceLetter());
                                        server.botPickedAndPlacedLetter(GameObject.this, botId, reply.letter, reply.cell);
                                    }
                                }
                                break;

                            case PICKED_AND_PLACED_LETTER:
                                ServerEvent.PickedAndPlacedLetter papEvent = (ServerEvent.PickedAndPlacedLetter) e;
                                if(papEvent.game.equals(GameObject.this)){
                                    for(BotId botId : ais.keySet()){
                                        if(!papEvent.playerName.equals(botId.botName)){
                                            AI ai = ais.get(botId);
                                            Msg.PlaceLetter reply = (Msg.PlaceLetter) ai.handleServerMessageAndProduceReply(
                                                    new Msg.RequestPlaceLetter(papEvent.letter, papEvent.playerName));
                                            server.botPlacedLetter(GameObject.this, botId, reply.get());
                                        }
                                    }
                                }
                                break;
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
                System.out.println("GameObject.runAI() is finished.");
            }
        }).start();
    }

    public boolean containsPlayer(UserId player) {
        return players.contains(player);
    }

    public GameObject(int numPlayers, UserId host, int numCols, int numRows){
        this.numPlayers = numPlayers;
        this.host = host;
        this.numCols = numCols;
        this.numRows = numRows;
        numCellsLeftToFill = numCols * numRows;
    }

    public boolean isFinished(){
        return state == State.FINISHED;
    }

    public void addHuman(UserId player, String name){
        players.add(player);
        playerNames.add(name);
        GridModel grid = new GridModel(numCols, numRows);
        grids.put(player, grid);
    }

    public void addBot(BotId bot){
        players.add(bot);
        GridModel grid = new GridModel(numCols, numRows);
        grids.put(bot, grid);
        AI ai = new AI();
        ais.put(bot, ai);
        playerNames.add(bot.botName);
        ai.initialize(grid);
    }

    public void pickAndPlaceLetter(PlayerId player, char letter, Cell cell){
        assertState(State.PICKING_AND_PLACING);
        GridModel grid = grids.get(player);
        grid.setCharAtCell(letter, cell);
        pickedLetter = letter;
        state = State.PLACING;
        numWaitingForPlace = numPlayers - 1;
    }

    public boolean placeLetter(PlayerId player, Cell cell){
        assertState(State.PLACING);
        GridModel grid = grids.get(player);
        grid.setCharAtCell(pickedLetter, cell);
        numWaitingForPlace --;
        boolean everyoneHasPlaced = numWaitingForPlace == 0;
        if(everyoneHasPlaced){
            activePlayerIndex = (activePlayerIndex + 1) % numPlayers;
            numCellsLeftToFill --;
            boolean gameOver = numCellsLeftToFill == 0;
            if(gameOver){
                state = State.FINISHED;
            }else{
                state = State.PICKING_AND_PLACING;
            }
            return true;
        }
        return false; //Not next turn yet
    }

    private void assertState(State expected){
        if(state != expected){
            throw new RuntimeException("State " + state + " != " + expected);
        }
    }

    public PlayerId getActivePlayer(){
        return players.get(activePlayerIndex);
    }

    public String getActivePlayerName(){
        return playerNames.get(activePlayerIndex);
    }


    public UserId getHost(){
        return host;
    }

    public boolean isReadyToStart(){
        return players.size() == numPlayers;
    }

    public int getNumPlayers(){
        return players.size();
    }

    public String toString(){
        return "Game(" + host + "){#" + numPlayers + ", " + players + "}";
    }

}
