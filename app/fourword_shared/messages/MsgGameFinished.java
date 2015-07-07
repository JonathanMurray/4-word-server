package fourword_shared.messages;

import fourword_shared.model.GameResult;

/**
 * Created by jonathan on 2015-06-26.
 */
public class MsgGameFinished extends Msg<ServerMsg> {
    public GameResult result;

    public MsgGameFinished( GameResult result) {
        super(ServerMsg.GAME_FINISHED);
        this.result = result;
    }

    public String toString(){
        return type() + "(" + result + ")";
    }
}
