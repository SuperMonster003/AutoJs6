// noinspection JSUnusedLocalSymbols,UnnecessaryLocalVariableJS

/**
 * @param {ScriptRuntime} scriptRuntime
 * @param {org.mozilla.javascript.Scriptable | global} scope
 * @return {Internal.Notice}
 */
module.exports = function (scriptRuntime, scope) {

    const AppUtils = org.autojs.autojs.runtime.api.AppUtils;
    const NotificationUtils = org.autojs.autojs.util.NotificationUtils;
    const NotificationCompat = androidx.core.app.NotificationCompat;
    const Builder = androidx.core.app.NotificationCompat.Builder;
    const BigTextStyle = androidx.core.app.NotificationCompat.BigTextStyle;
    const Notification = android.app.Notification;
    const NotificationManager = android.app.NotificationManager;

    // @Caution by SuperMonster003 on May 6, 2023.
    //  ! On device running with Android 7.x,
    //  ! importing NotificationManagerCompat will cause java.lang.ClassNotFoundException:
    //  ! Didn't find class "android.app.NotificationChannel" on path: DexPathList ...

    //  const NotificationManagerCompat = androidx.core.app.NotificationManagerCompat;

    const configDefaults = {
        /**
         * @type {number}
         * @const
         */
        defaultStaticNotificationId: (/* @IIFE */ () => {
            // noinspection JSValidateTypes
            return new java.lang.String('script_notification').hashCode();
        })(),
        /**
         * @type {string}
         * @const
         */
        defaultStaticChannelId: 'script_channel',
        useScriptNameAsDefaultChannelId: true,
        useDynamicDefaultNotificationId: true,
        enableChannelInvalidModificationWarnings: true,
    };

    const config = (/* @IIFE */ () => {

        /**
         * @implements {Notice.Config}
         */
        const Config = function () {
            /* Empty body. */
        };

        Config.prototype = {
            constructor: Config,

            useScriptNameAsDefaultChannelId: configDefaults.useScriptNameAsDefaultChannelId,
            useDynamicDefaultNotificationId: configDefaults.useDynamicDefaultNotificationId,
            enableChannelInvalidModificationWarnings: configDefaults.enableChannelInvalidModificationWarnings,

            defaultTitle: null,
            defaultContent: null,
            defaultBigContent: null,
            defaultAppendScriptName: null,
            defaultAutoCancel: null,
            defaultIsSilent: null,
            defaultPriority: null,

            defaultChannelName: null,
            defaultChannelDescription: null,

            defaultImportanceForChannel: null,
            defaultEnableVibrationForChannel: null,
            defaultVibrationPatternForChannel: null,
            defaultEnableLightsForChannel: null,
            defaultLightColorForChannel: null,
            defaultLockscreenVisibilityForChannel: null,
        };

        return new Config();
    })();

    const service = context.getSystemService(NotificationManager);

    const channel = (/* @IIFE */ () => {
        /**
         * @implements {Notice.Channel}
         */
        const Channel = function () {
            /* Empty body. */
        };

        Channel.prototype = {
            constructor: Channel,
            create(id, options) {
                if (species.isObject(arguments[0])) {
                    /** @type {Notice.Channel.Options} */
                    let opt = arguments[0];
                    return this.create(opt.id, opt);
                }
                /** @type {Notice.Channel.Options} */
                let opt = options || {};

                let niceId = _.getNiceChannelId(id);

                let niceName = (/* @IIFE */ () => {
                    if ('name' in opt) {
                        return String(opt.name);
                    }
                    let v = config.defaultChannelName;
                    return isNullish(v) ? null : String(v);
                })();

                let niceDescription = (/* @IIFE */ () => {
                    if ('description' in opt) {
                        return String(opt.description);
                    }
                    let v = config.defaultChannelDescription;
                    return isNullish(v) ? null : String(v);
                })();

                let niceImportance = (() => {
                    let importance = 'importance' in opt ? opt.importance : config.defaultImportanceForChannel;
                    if (isNullish(importance)) {
                        return null;
                    }
                    if (typeof importance === 'number') {
                        return importance;
                    }
                    if (typeof importance === 'string') {
                        switch (importance.toLowerCase()) {
                            case 'unspecified':
                                return NotificationManager.IMPORTANCE_UNSPECIFIED;
                            case 'none':
                                return NotificationManager.IMPORTANCE_NONE;
                            case 'min':
                                return NotificationManager.IMPORTANCE_MIN;
                            case 'low':
                                return NotificationManager.IMPORTANCE_LOW;
                            case 'default':
                                return NotificationManager.IMPORTANCE_DEFAULT;
                            case 'high':
                                return NotificationManager.IMPORTANCE_HIGH;
                            case 'max':
                                return NotificationManager.IMPORTANCE_MAX;
                            default:
                                throw TypeError(`Unknown importance (${importance})`);
                        }
                    }
                    throw TypeError(`Unknown species of importance (${species(importance)})`);
                })();

                let niceEnableVibration = (/* @IIFE */ () => {
                    if ('enableVibration' in opt) {
                        return Boolean(opt.enableVibration);
                    }
                    let v = config.defaultEnableVibrationForChannel;
                    return isNullish(v) ? null : Boolean(v);
                })();

                let niceVibrationPattern = (/* @IIFE */ () => {
                    if ('vibrationPattern' in opt) {
                        return opt.vibrationPattern;
                    }
                    let v = config.defaultVibrationPatternForChannel;
                    return isNullish(v) ? null : v;
                })();

                let niceEnableLights = (/* @IIFE */ () => {
                    if ('enableLights' in opt) {
                        return Boolean(opt.enableLights);
                    }
                    let v = config.defaultEnableLightsForChannel;
                    return isNullish(v) ? null : Boolean(v);
                })();

                let niceLightColor = (/* @IIFE */ () => {
                    if ('lightColor' in opt) {
                        return s13n.color(opt.lightColor);
                    }
                    let v = config.defaultLightColorForChannel;
                    return isNullish(v) ? null : s13n.color(v);
                })();

                let niceLockscreenVisibility = (() => {
                    let lockscreenVisibility = 'lockscreenVisibility' in opt
                        ? opt.lockscreenVisibility
                        : config.defaultLockscreenVisibilityForChannel;
                    if (isNullish(lockscreenVisibility)) {
                        return null;
                    }
                    if (typeof lockscreenVisibility === 'number') {
                        return lockscreenVisibility;
                    }
                    if (typeof lockscreenVisibility === 'string') {
                        switch (lockscreenVisibility.toLowerCase()) {
                            case 'no_override' :
                                return NotificationManager.VISIBILITY_NO_OVERRIDE;
                            case 'public' :
                                return Notification.VISIBILITY_PUBLIC;
                            case 'private' :
                                return Notification.VISIBILITY_PRIVATE;
                            case 'secret' :
                                return Notification.VISIBILITY_SECRET;
                            default:
                                throw TypeError(`Unknown lockscreenVisibility "${lockscreenVisibility}" in ${'lockscreenVisibility' in opt ? 'options' : 'config'}`);
                        }
                    }
                    throw TypeError(`Unknown species "${species(lockscreenVisibility)}" of lockscreenVisibility`);
                })();

                (function invalidModificationWarnings$iiFe() {
                    let channel = this;
                    if (!config.enableChannelInvalidModificationWarnings || !channel.contains(niceId)) {
                        return;
                    }
                    Object.keys(opt).forEach((k) => {
                        if (k === 'name' || k === 'description') {
                            return;
                        }
                        if (k === 'importance') {
                            let channelImportance = channel.get(niceId).getImportance();
                            if (channel.get(niceId).hasUserSetImportance()) {
                                console.warn(`Property "${k}" for channel with ID "${niceId}" will not take effect, `
                                    + `as the user has altered importance on this channel`);
                            } else if (niceImportance > channelImportance) {
                                console.warn(`Property "${k}" for channel with ID "${niceId}" will not take effect, `
                                    + `as the new ${k} "${opt[k]}" should not be higher than the current value "${channelImportance}"`);
                            }
                        } else {
                            console.warn(`Property "${k}" for channel with ID "${niceId}" will not take effect, `
                                + `as "${k}" of channel can not be modified programmatically `
                                + `after the channel has created and submitted to the notification manager`);
                        }
                    });
                }).call(this);

                NotificationUtils.createChannel(
                    niceId,
                    /** @type {string} */
                    niceName,
                    /** @type {string} */
                    niceDescription,
                    /** @type {java.lang.Integer} */
                    niceImportance,
                    /** @type {java.lang.Boolean} */
                    niceEnableVibration,
                    /** @type {number[]} */
                    niceVibrationPattern,
                    /** @type {java.lang.Boolean} */
                    niceEnableLights,
                    /** @type {java.lang.Integer} */
                    niceLightColor,
                    /** @type {java.lang.Integer} */
                    niceLockscreenVisibility,
                );

                return niceId;
            },
            createIfNeeded(id, options) {
                let channelId = (/* @IIFE */ () => {
                    if (species.isObject(arguments[0])) {
                        /** @type {Notice.Options} */
                        let opt = arguments[0];
                        id = opt.channelId;
                    }
                    return _.getNiceChannelId(id);
                })();
                if (!this.contains(channelId)) {
                    this.create(channelId, species.isObject(arguments[0]) ? /* as options */ arguments[0] : options);
                }
            },
            remove(id) {
                if (!this.contains(id)) {
                    return false;
                }
                service.deleteNotificationChannel(id);
                return true;
            },
            contains(id) {
                return this.getAll().some(o => o.getId() === id);
            },
            get(id) {
                if (isNullish(id)) {
                    return null;
                }
                if (util.version.sdkInt < util.versionCodes.O) {
                    return null;
                }
                return service.getNotificationChannel(String(id));
            },
            getAll() {
                if (util.version.sdkInt < util.versionCodes.O) {
                    return [];
                }
                return service.getNotificationChannels().toArray();
            },
        };

        return new Channel();
    })();

    let _ = {
        NoticeCtor: (/* @IIFE */ () => {
            /**
             * @implements Internal.Notice
             */
            const NoticeCtor = function () {
                /** @global */
                const notice = function () {
                    if (arguments[0] instanceof Builder) {

                        // @Signature notice(builder: androidx.core.app.NotificationCompat.Builder, options?: Notice.Options): void;

                        let signature = `notice(builder: androidx.core.app.NotificationCompat.Builder, options?: Notice.Options): void`;

                        /** @type {androidx.core.app.NotificationCompat.Builder} */
                        let builder = arguments[0];

                        if (arguments.length > 2) {
                            throw TypeError(`"${signature}" can't take more than 2 arguments`);
                        }

                        if (arguments.length > 1) {
                            if (!species.isObject(arguments[1])) {
                                throw TypeError(`"${signature}" invoked with incorrect type of argument "options"`);
                            }
                        }

                        /** @type {Notice.Options} */
                        let opt = Object.create(arguments[1] || {});

                        let niceChannelId = _.getNiceChannelId(opt.channelId);

                        builder.setChannelId(niceChannelId);
                        channel.createIfNeeded(niceChannelId);

                        _.appendScriptName(opt);

                        if (!isNullish(opt.title)) {
                            builder.setContentTitle(String(opt.title));
                        }

                        if (!isNullish(opt.content)) {
                            builder.setContentText(String(opt.content));
                        }

                        if (!isNullish(opt.bigContent)) {
                            builder.setStyle(new BigTextStyle().bigText(String(opt.bigContent)));
                        }

                        let niceNotificationId = _.getNiceNotificationId(opt);

                        NotificationUtils.notice(
                            builder,
                            /** @type {?java.lang.Integer} */
                            niceNotificationId,
                            /** @type {?java.lang.Boolean} */
                            _.getNiceAutoCancel(opt),
                            /** @type {?java.lang.Boolean} */
                            _.getNiceIsSilent(opt),
                            /** @type {?Intent} */
                            _.getNiceIntent(opt),
                            /** @type {?java.lang.Integer} */
                            _.getNicePriority(opt),
                        );

                        return niceNotificationId;
                    }

                    let asContentType = o => typeof o === 'string' || o === null;

                    if (asContentType(arguments[0]) && !asContentType(arguments[1])) {

                        // @Signature notice(content: string, options?: Notice.Options): void;

                        let signature = `notice(content: string, options?: Notice.Options): void`;

                        if (arguments.length > 2) {
                            throw TypeError(`"${signature}" can't take more than 2 arguments`);
                        }

                        if (arguments.length > 1) {
                            if (!species.isObject(arguments[1])) {
                                throw TypeError(`"${signature}" invoked with incorrect type of argument "options"`);
                            }
                        }

                        let opt = Object.create(arguments[1] || {});
                        if (isNullish(opt.content)) {
                            opt.content = arguments[0];
                        }
                        return notice(opt);
                    }

                    if (asContentType(arguments[0]) && asContentType(arguments[1])) {

                        // @Signature notice(title: string, content: string, options?: Notice.Options): void;

                        let signature = `notice(title: string, content: string, options?: Notice.Options): void`;

                        if (arguments.length > 3) {
                            throw TypeError(`"${signature}" can't take more than 3 arguments`);
                        }

                        if (arguments.length > 2) {
                            if (!species.isObject(arguments[2])) {
                                throw TypeError(`"${signature}" invoked with incorrect type of argument "options"`);
                            }
                        }

                        let title = arguments[0];
                        let content = arguments[1];
                        /** @type {Notice.Options} */
                        let opt = Object.create(arguments[2] || {});
                        if (isNullish(opt.title) && !isNullish(title)) {
                            opt.title = title;
                        }
                        if (isNullish(opt.content) && !isNullish(content)) {
                            opt.content = content;
                        }
                        return notice(opt);
                    }

                    // @Signature notice(options: Notice.Options): void;

                    /** @type {Notice.Options} */
                    let opt = Object.create(arguments[0] || {});

                    if (isNullish(opt.title) && isNullish(opt.content) && isNullish(opt.bigContent)) {
                        opt.title = NotificationUtils.defaultTitle;
                        opt.content = NotificationUtils.defaultContent;
                    }

                    _.appendScriptName(opt);

                    let niceChannelId = _.getNiceChannelId(opt.channelId);

                    let niceTitle = (/* @IIFE */ () => {
                        if (!isNullish(opt.title)) {
                            return String(opt.title);
                        }
                        let v = config.defaultTitle;
                        return isNullish(v) ? null : String(v);
                    })();

                    let niceContent = (/* @IIFE */ () => {
                        if (!isNullish(opt.content)) {
                            return String(opt.content);
                        }
                        let v = config.defaultContent;
                        return isNullish(v) ? null : String(v);
                    })();

                    let niceBigContent = (/* @IIFE */ () => {
                        if (!isNullish(opt.bigContent)) {
                            return String(opt.bigContent);
                        }
                        let v = config.defaultBigContent;
                        return isNullish(v) ? null : String(v);
                    })();

                    channel.createIfNeeded(niceChannelId);

                    let niceNotificationId = _.getNiceNotificationId(opt);

                    NotificationUtils.notice(
                        niceChannelId,
                        niceTitle,
                        niceContent,
                        niceBigContent,
                        /** @type {?java.lang.Integer} */
                        niceNotificationId,
                        /** @type {?java.lang.Boolean} */
                        _.getNiceAutoCancel(opt),
                        /** @type {?java.lang.Boolean} */
                        _.getNiceIsSilent(opt),
                        /** @type {?Intent} */
                        _.getNiceIntent(opt),
                        /** @type {?java.lang.Integer} */
                        _.getNicePriority(opt),
                    );

                    return niceNotificationId;
                };

                return Object.assign(notice, NoticeCtor.prototype);
            };

            NoticeCtor.prototype = {
                constructor: NoticeCtor,
                channel: channel,
                isEnabled() {
                    return NotificationUtils.isEnabled();
                },
                ensureEnabled() {
                    NotificationUtils.ensureEnabled();
                },
                launchSettings() {
                    NotificationUtils.launchSettings();
                },
                config(cfg) {
                    util.ensureObjectType(cfg);

                    Object.entries(cfg).forEach((entry) => {
                        let [ k, v ] = entry;
                        if (!(k in config && isPrimitive(config[k]))) {
                            throw TypeError(`Unknown key "${k}" for notice.config`);
                        }
                        if (isNullish(v) /* to reset */) {
                            config[k] = k in configDefaults ? configDefaults[k] : null;
                        } else {
                            config[k] = v;
                        }
                    });
                },
                cancel(id) {
                    if (!isNullish(id)) {
                        let niceId = Number(id);
                        if (!isNaN(niceId)) {
                            if (util.version.sdkInt >= util.versionCodes.O) {
                                androidx.core.app.NotificationManagerCompat.from(context).cancel(niceId);
                            }
                        }
                    }
                },
                getBuilder: () => {
                    let builder = NotificationUtils.getSimpleBuilder();
                    if (!isNullish(config.defaultPriority)) {
                        builder.setPriority(_.getNicePriority({ priority: config.defaultPriority }));
                    }
                    return builder;
                },
            };

            return NoticeCtor;
        })(),
        getNiceChannelId(id) {
            if (!isNullish(id)) {
                return String(id);
            }
            return config.useScriptNameAsDefaultChannelId
                ? _.getDefaultChannelId()
                : configDefaults.defaultStaticChannelId;
        },
        getDefaultChannelId() {
            return engines.myEngine().getSource().getFullName();
        },
        /** @returns {number} */
        getNiceNotificationId(opt) {
            if ('notificationId' in opt) {
                let id = Number(opt.notificationId);
                if (!isNaN(id)) {
                    return id;
                }
            }
            return config.useDynamicDefaultNotificationId
                ? NotificationUtils.getDefaultNotificationId()
                : configDefaults.defaultStaticNotificationId;
        },
        getNiceIntent(opt) {
            let intent = opt.intent;

            if (typeof intent === 'string') {
                if (AppUtils.isActivityShortForm(intent)) {
                    let prop = runtime.getProperty(`class.${intent}`);
                    if (!prop) {
                        throw Error(`Activity short form ${intent} not found`);
                    }
                    return new Intent(context, prop).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                let rexWebSiteWithoutProtocol = /^(www.)?[a-z0-9]+(\.[a-z]{2,}){1,3}(#?\/?[a-zA-Z0-9#]+)*\/?(\?[a-zA-Z0-9-_]+=[a-zA-Z0-9-%]+&?)?$/;
                if (rexWebSiteWithoutProtocol.test(intent)) {
                    // noinspection HttpUrlsUsage
                    intent = `http://${intent}`;
                }
                return app.intent({ data: intent });
            }
            if (species.isObject(intent)) {
                return app.intent(intent);
            }
            return intent instanceof Intent ? intent : null;
        },
        getNicePriority(opt) {
            let priority = 'priority' in opt ? opt.priority : null;
            if (isNullish(priority)) {
                return null;
            }
            if (typeof priority === 'number') {
                return priority;
            }
            if (typeof priority === 'string') {
                switch (priority.toLowerCase()) {
                    case 'default':
                        return NotificationCompat.PRIORITY_DEFAULT;
                    case 'low':
                        return NotificationCompat.PRIORITY_LOW;
                    case 'min':
                        return NotificationCompat.PRIORITY_MIN;
                    case 'high':
                        return NotificationCompat.PRIORITY_HIGH;
                    case 'max':
                        return NotificationCompat.PRIORITY_MAX;
                    default:
                        throw TypeError(`Unknown priority (${priority})`);
                }
            }
            throw TypeError(`Unknown species of priority (${species(priority)})`);
        },
        getNiceAutoCancel(opt) {
            if ('autoCancel' in opt) {
                return opt.autoCancel;
            }
            let v = config.defaultAutoCancel;
            return isNullish(v) ? null : Boolean(v);
        },
        getNiceIsSilent(opt) {
            if ('isSilent' in opt) {
                return opt.isSilent;
            }
            let v = config.defaultIsSilent;
            return isNullish(v) ? null : Boolean(v);
        },
        appendScriptName(opt) {
            util.ensureObjectType(opt);
            let strategy = opt.appendScriptName;
            if (strategy === false) {
                return;
            }
            if (isNullish(strategy)) {
                let def = config.defaultAppendScriptName;
                if (isNullish(def) || def === false) {
                    return;
                }
                strategy = def;
            }
            let append = strategy === true ? 'auto' : String(strategy);
            let scriptName = _.getDefaultChannelId();
            switch (append) {
                case 'auto':
                    if (!isNullish(opt.bigContent)) {
                        opt.bigContent = `${opt.bigContent} (${scriptName})`;
                    } else if (!isNullish(opt.content)) {
                        opt.content = `${opt.content} (${scriptName})`;
                    } else if (!isNullish(opt.title)) {
                        opt.title = `${opt.title} (${scriptName})`;
                    }
                    break;
                case 'title':
                    opt.title = isNullish(opt.title) ? scriptName : `${opt.title} (${scriptName})`;
                    break;
                case 'content':
                    opt.content = isNullish(opt.content) ? scriptName : `${opt.content} (${scriptName})`;
                    break;
                case 'bigContent':
                    opt.bigContent = isNullish(opt.bigContent) ? scriptName : `${opt.bigContent} (${scriptName})`;
                    break;
                default:
                    throw TypeError(`Unknown destination (${append}) for script name to append to`);
            }
        },
    };

    /**
     * @type {Internal.Notice}
     */
    const notice = new _.NoticeCtor();

    return notice;
};