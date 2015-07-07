package controllers;

import play.mvc.Http;

/**
 * Created by jonathan on 2015-07-07.
 */
public class InOut {
    final Http.Inbound inbound;
    final Http.Outbound outbound;


    public InOut(Http.Inbound inbound, Http.Outbound outbound) {
        this.inbound = inbound;
        this.outbound = outbound;
    }
}
