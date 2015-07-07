package fourword_shared.messages;

/**
 * Created by jonathan on 2015-06-26.
 */
public enum ServerMsg implements MsgType {
    OK,
    NO,


    DO_PLACE_LETTER,
    DO_PICK_AND_PLACE_LETTER,
    GAME_FINISHED,
    WAITING_FOR_PLAYER_MOVE,
    GAME_PLAYERS_TURN,

    //Not response to a specific client msg
    YOU_ARE_INVITED,
    YOU_WERE_KICKED,
    LOBBY_STATE,
    GAME_IS_STARTING,
    GAME_PLAYER_DONE_THINKING,
    ONLINE_PLAYERS,
    GAME_CRASHED;
}
