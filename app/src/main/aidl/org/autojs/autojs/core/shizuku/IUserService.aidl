package org.autojs.autojs.core.shizuku;

interface IUserService {

    void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    String execCommand(String command) = 2;

    String currentPackage() = 11;
    String currentActivity() = 12;
    String currentComponent() = 13;
    String currentComponentShort() = 14;

}