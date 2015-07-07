package fourword_shared.messages;

import fourword_shared.model.Lobby;

/**
 * Created by jonathan on 2015-06-26.
 */
public class MsgLobbyState extends Msg {
    public final Lobby lobby;

    public MsgLobbyState(Lobby lobby) {
        super(ServerMsg.LOBBY_STATE);
        this.lobby = lobby.getCopy();
    }

    public String toString(){
        return "MsgLobbyState(" + lobby + ")";
    }

}
