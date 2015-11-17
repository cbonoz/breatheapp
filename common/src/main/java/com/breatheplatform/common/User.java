package com.breatheplatform.common;

import java.util.Date;

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
    private final String user_name;
    private final Date birthday;


    public User(int i, String n, Date bday) {
        id=i;
        user_name=n;
        birthday=bday;
    }

    public int getId() {
        return id;
    }
    public String getName() {
        return user_name;
    }
    public Date getBirthday() {
        return birthday;
    }
}
