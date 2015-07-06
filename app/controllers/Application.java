package controllers;

import play.Logger;
import play.mvc.Http;
import play.mvc.WebSocketController;

public class Application extends WebSocketController {

    public static void connect() {
        while(inbound.isOpen()){
            Http.WebSocketEvent e = await(inbound.nextEvent());
            if(e instanceof Http.WebSocketFrame) {
                Http.WebSocketFrame frame = (Http.WebSocketFrame)e;

                if(!frame.isBinary) {
                    if(frame.textData.equals("quit")) {
                        outbound.send("Bye!");
                        disconnect();
                    } else {
                        outbound.send("Echo: %s", frame.textData);
                    }
                }
            }
            if(e instanceof Http.WebSocketClose) {
                Logger.info("Socket closed!");
            }
        }
    }

}