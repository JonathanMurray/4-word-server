package controllers;

/**
 * Created by jonathan on 2015-07-09.
 */
public class UserId extends PlayerId{
    final String idString;

    public UserId(String idString) {
        this.idString = idString;
    }

    public String toString(){
        return idString;
    }
}
