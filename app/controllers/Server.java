package controllers;

import fourword_shared.messages.Msg;
import fourword_shared.messages.ServerMsg;
import play.libs.F;

/**
 * Created by jonathan on 2015-07-08.
 */
public class Server {

    public final static Server INSTANCE = new Server();
    public final static int MAX_ARCHIVED_EVENTS = 100;

    private F.ArchivedEventStream<Msg<ServerMsg>> events = new F.ArchivedEventStream(MAX_ARCHIVED_EVENTS);

    F.EventStream<Msg<ServerMsg>> stream(){
        return events.eventStream();
    }

    void publish(Msg<ServerMsg> msg){
        events.publish(msg);
    }

    static{

    }

}
