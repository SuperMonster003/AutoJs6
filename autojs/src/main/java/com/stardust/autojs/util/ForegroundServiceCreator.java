package com.stardust.autojs.util;

import android.app.Service;
import android.content.Intent;

import com.stardust.app.GlobalAppContext;

/**
 * Created by SuperMonster003 on Apr 11, 2022.
 */
public class ForegroundServiceCreator {

    public int notificationId;
    public Service service;
    public Class<?> className;
    public Intent intent;
    public String serviceName;
    public String serviceDescription;
    public String notificationTitle;
    public String notificationContent;

    private static class Creator {
        Intent intent;
        Class<?> className;
        Service service;
        Notification notification;

        public Creator(Class<?> className, Intent intent, Service service, Notification notification) {
            this.className = className;
            this.intent = intent;
            this.service = service;
            this.notification = notification;
        }

        public static class Service {
            android.app.Service context;
            String name;
            String description;

            Service(android.app.Service context, String name, String description) {
                this.context = context;
                this.name = name;
                this.description = description;
            }
        }

        private static class Notification {
            int id;
            String title;
            String content;

            Notification(int id, String title, String content) {
                this.id = id;
                this.title = title;
                this.content = content;
            }
        }
    }

    private ForegroundServiceCreator(Creator creator) {
        Creator.Service service = creator.service;
        Creator.Notification notification = creator.notification;

        this.notificationId = notification.id;
        this.service = service.context;
        this.intent = creator.intent;
        this.className = creator.className;
        this.serviceName = service.name;
        this.serviceDescription = service.description;
        this.notificationTitle = notification.title;
        this.notificationContent = notification.content;
    }

    public static class Builder {
        private final Service service;

        private int notificationId = 0;
        private Class<?> className;
        private Intent intent;
        private String serviceName;
        private String serviceDescription;
        private String notificationTitle;
        private String notificationContent;

        public Builder(Service context) {
            this.service = context;
        }

        public Builder setNotificationId(int notificationId) {
            this.notificationId = notificationId;
            return this;
        }

        public Builder setIntent(Intent intent) {
            this.intent = intent;
            return this;
        }

        public Builder setClassName(Class<?> className) {
            this.className = className;
            return this;
        }

        public Builder setServiceName(int resId) {
            this.serviceName = GlobalAppContext.getString(resId);
            return this;
        }

        public Builder setServiceName(String s) {
            this.serviceName = s;
            return this;
        }

        public Builder setServiceDescription(int resId) {
            this.serviceDescription = GlobalAppContext.getString(resId);
            return this;
        }

        public Builder setServiceDescription(String s) {
            this.serviceDescription = s;
            return this;
        }

        public Builder setNotificationTitle(int resId) {
            this.notificationTitle = GlobalAppContext.getString(resId);
            return this;
        }

        public Builder setNotificationTitle(String s) {
            this.notificationTitle = s;
            return this;
        }

        public Builder setNotificationContent(int resId) {
            this.notificationContent = GlobalAppContext.getString(resId);
            return this;
        }

        public Builder setNotificationContent(String s) {
            this.notificationContent = s;
            return this;
        }

        public ForegroundServiceCreator create() {
            return new ForegroundServiceCreator(new Creator(className,
                    intent,
                    new Creator.Service(service, serviceName, serviceDescription),
                    new Creator.Notification(notificationId, notificationTitle, notificationContent)));
        }
    }

}