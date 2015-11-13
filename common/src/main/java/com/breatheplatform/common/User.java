package com.breatheplatform.common;

/**
 * Created by cbono on 11/11/15.
 */

/*
User class will be the basis of a future authentication system for the app
so that sensor data can be tied to specific individuals
*/
    //TODO implement user authentication

public class User {
    private final int id;

    public User(int i) {
        id=i;
    }

    public int getId() {
        return id;
    }
}
