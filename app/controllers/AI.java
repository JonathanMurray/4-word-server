package controllers;

import fourword_shared.messages.*;
import fourword_shared.model.Cell;
import fourword_shared.model.GridModel;
import fourword_shared.model.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by jonathan on 2015-06-25.
 */
public class AI {
    private GridModel grid;
    private List<Cell> emptyCells;

    public void initialize(GridModel grid){
        emptyCells = new ArrayList<Cell>();
        this.grid = grid;
        for(int x = 0; x < grid.getNumCols(); x++){
            for(int y = 0; y < grid.getNumRows(); y++){
                Cell cell = new Cell(x,y);
                emptyCells.add(cell);
            }
        }
    }

    public void setLetter(Cell cell, char letter){
        assertIsInitialized();
        grid.setCharAtCell(letter, cell);
        emptyCells.remove(cell);
    }

    public Msg<ClientMsg> handleServerMessageAndProduceReply(Msg<ServerMsg> msg){
        assertIsInitialized();
        Cell randomEmptyCell;
        switch (msg.type()){
            case DO_PICK_AND_PLACE_LETTER:
                char letter = Util.randomLetter();
                randomEmptyCell = randomEmptyCell();
                grid.setCharAtCell(letter, randomEmptyCell);
                return new Msg.PickAndPlaceLetter(letter, randomEmptyCell);
            case DO_PLACE_LETTER:
                randomEmptyCell = randomEmptyCell();
                grid.setCharAtCell(((Msg.RequestPlaceLetter)msg).letter, randomEmptyCell);
                return new Msg.PlaceLetter(randomEmptyCell);
            default:
                return null;
        }
//        throw new RuntimeException();
    }

    private Cell randomEmptyCell(){
        assertIsInitialized();
        return emptyCells.remove(new Random().nextInt(emptyCells.size()));
    }



    private void assertIsInitialized(){
        if(grid == null){
            throw new RuntimeException("AI has not been initialized with a grid!");
        }
    }

    public String toString(){
        return grid.toString();
    }

}
