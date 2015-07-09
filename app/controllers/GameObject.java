package controllers;

import fourword_shared.model.GridModel;

import java.util.ArrayList;

/**
 * Created by jonathan on 2015-06-27.
 */
public class GameObject {
    private int numPlayers;
    private UserId host;
    public ArrayList<UserId> players = new ArrayList();
    public ArrayList<GridModel> grids = new ArrayList();
    private final int numCols;
    private final int numRows;


    public GameObject(int numPlayers, UserId host, int numCols, int numRows){
        this.numPlayers = numPlayers;
        this.host = host;
        this.numCols = numCols;
        this.numRows = numRows;
    }

    public void join(UserId player){
        players.add(player);
        GridModel grid = new GridModel(numCols, numRows);
        grids.add(grid);
//        socket.initializeWithGrid(grid);
    }

    public UserId getHost(){
        return host;
    }

    public boolean isReadyToStart(){
        return players.size() == numPlayers;
    }

    public String toString(){
        return "Game(" + host + "){#" + numPlayers + ", " + players + "}";
    }

}
