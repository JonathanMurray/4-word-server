package controllers;

import fourword_shared.model.Lobby;

/**
 * Created by jonathan on 2015-07-08.
 */
public class ServerEvent {

    static class LoggedIn extends ServerEvent{
        final String player;

        LoggedIn(String player) {
            this.player = player;
        }
    }

    static class LoggedOut extends ServerEvent{
        final String player;

        LoggedOut(String player) {
            this.player = player;
        }
    }

    static class CreatedLobby extends ServerEvent{
        final String creator;

        CreatedLobby(String creator) {
            this.creator = creator;
        }
    }

    static class InvitedToLobby extends ServerEvent{
        final String invited;
        final Lobby lobby;

        InvitedToLobby(String invited, Lobby lobby) {
            this.invited = invited;
            this.lobby = lobby;
        }
    }

    static class KickedFromLobby extends ServerEvent{
        final String kicker;
        final String kicked;

        KickedFromLobby(String kicker, String kicked) {
            this.kicker = kicker;
            this.kicked = kicked;
        }
    }

    static class DeclinedInvite extends ServerEvent{
        final String player;
        final Lobby lobby;

        DeclinedInvite(String player, Lobby lobby) {
            this.player = player;
            this.lobby = lobby;
        }
    }

    static class JoinedLobby extends ServerEvent{
        final String player;
        final Lobby lobby;

        JoinedLobby(String player, Lobby lobby) {
            this.player = player;
            this.lobby = lobby;
        }
    }

    static class LobbyGameStarting extends ServerEvent{
        final Lobby lobby;

        LobbyGameStarting(Lobby lobby) {
            this.lobby = lobby;
        }
    }

    static class LobbyDimensionsChanged extends ServerEvent{
        final Lobby lobby;

        LobbyDimensionsChanged(Lobby lobby) {
            this.lobby = lobby;
        }
    }

    static class GameFinished extends ServerEvent{
        final GameObject game;

        GameFinished(GameObject game) {
            this.game = game;
        }
    }

    static class GameCrashed extends ServerEvent{
        final GameObject game;

        GameCrashed(GameObject game) {
            this.game = game;
        }
    }


}
