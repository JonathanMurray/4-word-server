package controllers;

import fourword_shared.model.Cell;
import fourword_shared.model.GameSettings;
import fourword_shared.model.Lobby;

import java.io.Serializable;

/**
 * Created by jonathan on 2015-07-08.
 */
public abstract class ServerEvent {

    public static enum Type{
        LOGIN,
        LOGOUT,
        CREATE_LOBBY,
        INVITED_TO_LOBBY,
        KICKED_FROM_LOBBY,
        DECLINED_INVITE,
        JOINED_LOBBY,
        LEFT_LOBBY,
        LOBBY_GAME_STARTING,
        GAME_PLAYERS_TURN,
        LOBBY_REMOVED,
        LOBBY_NEW_HOST,
        GAME_FINISHED,
        GAME_CRASHED,
        PICKED_AND_PLACED_LETTER,
        PLACED_LETTER,
        GAME_HAS_STARTED,
        GAME_ENDED_EARLY,
        LOBBY_SET_VAR, GAME_SERVER_SET_LETTER;
    }

    public final Type type;

    public ServerEvent(Type type){
        this.type = type;
    }

    static class LoggedIn extends ServerEvent{
        final String player;

        LoggedIn(String player) {
            super(Type.LOGIN);
            this.player = player;
        }
    }

    static class LoggedOut extends ServerEvent{
        final String player;

        LoggedOut(String player) {
            super(Type.LOGOUT);
            this.player = player;
        }
    }

    static class CreatedLobby extends ServerEvent{
        final UserId creator;
        final String creatorName;

        CreatedLobby(UserId creator, String creatorName) {
            super(Type.CREATE_LOBBY);
            this.creator = creator;
            this.creatorName = creatorName;
        }
    }

    static class InvitedToLobby extends ServerEvent{
        final String inviter;
        final UserId invitedId;
        final String invitedName;
        final Lobby lobby;

        InvitedToLobby(String inviter, UserId invitedId, String invitedName, Lobby lobby) {
            super(Type.INVITED_TO_LOBBY);
            this.inviter = inviter;
            this.invitedId = invitedId;
            this.invitedName = invitedName;
            this.lobby = lobby;
        }
    }

    static class KickedFromLobby extends ServerEvent{
        final String kicker;
        final PlayerId kickedId;
        final String kickedName;
        final Lobby lobby;

        public KickedFromLobby(String kicker, Lobby lobby, PlayerId kickedId, String kickedName) {
            super(Type.KICKED_FROM_LOBBY);
            this.kicker = kicker;
            this.lobby = lobby;
            this.kickedId = kickedId;
            this.kickedName = kickedName;
        }
    }

    static class DeclinedInvite extends ServerEvent{
        final String player;
        final Lobby lobby;

        DeclinedInvite(String player, Lobby lobby) {
            super(Type.DECLINED_INVITE);
            this.player = player;
            this.lobby = lobby;
        }
    }

    static class JoinedLobby extends ServerEvent{
        final PlayerId playerId;
        final String playerName;
        final boolean isHuman;
        final Lobby lobby;

        private JoinedLobby(PlayerId playerId, String playerName, boolean isHuman, Lobby lobby) {
            super(Type.JOINED_LOBBY);
            this.playerId = playerId;
            this.playerName = playerName;
            this.isHuman = isHuman;
            this.lobby = lobby;
        }

        static JoinedLobby human(PlayerId playerId, String playerName, Lobby lobby){
            return new JoinedLobby(playerId, playerName, true, lobby);
        }

        static JoinedLobby bot(String botName, Lobby lobby){
            return new JoinedLobby(null, botName, false, lobby);
        }
    }

    static class LeftLobby extends ServerEvent{
        final PlayerId playerId;
        final String playerName;
        final Lobby lobby;

        LeftLobby(PlayerId playerId, String playerName, Lobby lobby) {
            super(Type.LEFT_LOBBY);
            this.playerId = playerId;
            this.playerName = playerName;
            this.lobby = lobby;
        }
    }

    static class LobbyGameStarting extends ServerEvent{
        final Lobby lobby;

        LobbyGameStarting(Lobby lobby) {
            super(Type.LOBBY_GAME_STARTING);
            this.lobby = lobby;
        }
    }

    static class GamePlayersTurn extends ServerEvent{
        final GameObject game;
        final PlayerId playerId;
        final String playerName;

        GamePlayersTurn(GameObject game, PlayerId playerId, String playerName) {
            super(Type.GAME_PLAYERS_TURN);
            this.game = game;
            this.playerId = playerId;
            this.playerName = playerName;
        }
    }

    static class LobbySetVar extends ServerEvent{
        final Lobby lobby;
        final GameSettings.Attribute var;
        final Serializable value;

        LobbySetVar(Lobby lobby, GameSettings.Attribute var, Serializable value) {
            super(Type.LOBBY_SET_VAR);
            this.lobby = lobby;
            this.var = var;
            this.value = value;
        }
    }

//    static class LobbyDimensionsChanged extends ServerEvent{
//        final Lobby lobby;
//
//        LobbyDimensionsChanged(Lobby lobby) {
//            super(Type.LOBBY_DIM_CHANGE);
//            this.lobby = lobby;
//        }
//    }

//    static class LobbyTimeLimitChanged extends ServerEvent{
//        final Lobby lobby;
//
//        LobbyTimeLimitChanged(Lobby lobby) {
//            super(Type.LOBBY_TIME_LIMIT_CHANGE);
//            this.lobby = lobby;
//        }
//    }

    static class LobbyNewHost extends ServerEvent{
        final Lobby lobby;
        final String playerName;
        public LobbyNewHost(Lobby lobby, String playerName) {
            super(Type.LOBBY_NEW_HOST);
            this.lobby = lobby;
            this.playerName = playerName;
        }
    }

    static class LobbyRemoved extends ServerEvent{
        final Lobby lobby;

        LobbyRemoved(Lobby lobby) {
            super(Type.LOBBY_REMOVED);
            this.lobby = lobby;
        }
    }

    static class GameFinished extends ServerEvent{
        final GameObject game;

        GameFinished(GameObject game) {
            super(Type.GAME_FINISHED);
            this.game = game;
        }
    }

    static class GameCrashed extends ServerEvent{
        final GameObject game;

        GameCrashed(GameObject game) {
            super(Type.GAME_CRASHED);
            this.game = game;
        }
    }

    static class PickedAndPlacedLetter extends ServerEvent{

        final GameObject game;
        final String playerName;
        final char letter;
        final Cell cell;

        PickedAndPlacedLetter(GameObject game, String playerName, char letter, Cell cell) {
            super(Type.PICKED_AND_PLACED_LETTER);
            this.game = game;
            this.playerName = playerName;
            this.letter = letter;
            this.cell = cell;
        }
    }

    static class PlacedLetter extends ServerEvent{
        final GameObject game;
        final String playerName;
        final Cell cell;

        PlacedLetter(GameObject game, String playerName, Cell cell) {
            super(Type.PLACED_LETTER);
            this.game = game;
            this.playerName = playerName;
            this.cell = cell;
        }
    }


    static class GameHasStarted extends ServerEvent {
        final GameObject game;

        public GameHasStarted(GameObject game) {
            super(Type.GAME_HAS_STARTED);
            this.game = game;
        }
    }

    static class GameServerSetLetter extends ServerEvent{
        final GameObject game;
        final Cell cell;
        final char letter;

        GameServerSetLetter(GameObject game, Cell cell, char letter) {
            super(Type.GAME_SERVER_SET_LETTER);
            this.game = game;
            this.cell = cell;
            this.letter = letter;
        }
    }

    static class GameEnded extends ServerEvent {
        final GameObject game;
        final String leaverName;
        final UserId leaverId;

        public GameEnded(GameObject game, String leaverName, UserId leaverId) {
            super(Type.GAME_ENDED_EARLY);
            this.game = game;
            this.leaverName = leaverName;
            this.leaverId = leaverId;
        }
    }
}
