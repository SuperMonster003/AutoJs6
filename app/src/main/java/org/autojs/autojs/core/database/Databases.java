package org.autojs.autojs.core.database;

public class Databases {

    public static Database openDatabase(String name, int version, String desc, long size){
        return new Database();
    }

}
