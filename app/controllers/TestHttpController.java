package controllers;

import play.mvc.Controller;
import play.mvc.results.Result;

/**
 * Created by jonathan on 2015-07-08.
 */
public class TestHttpController extends Controller {

    public static void index(){
        System.out.println("TestHttpController.index()");
        renderText("hello");
    }
}
