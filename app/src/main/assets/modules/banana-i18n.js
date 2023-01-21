( /* @ModuleIIFE */ () => {

    "use strict";

    var _createClass = function () {
        function defineProperties(target, props) {
            for (var i = 0; i < props.length; i++) {
                var descriptor = props[i];
                descriptor.enumerable = descriptor.enumerable || false;
                descriptor.configurable = true;
                if ("value" in descriptor) descriptor.writable = true;
                Object.defineProperty(target, descriptor.key, descriptor);
            }
        }

        return function (Constructor, protoProps, staticProps) {
            if (protoProps) defineProperties(Constructor.prototype, protoProps);
            if (staticProps) defineProperties(Constructor, staticProps);
            return Constructor;
        };
    }();

    var _typeof = typeof Symbol === "function" && typeof Symbol.iterator === "symbol" ? function (obj) {
        return typeof obj;
    } : function (obj) {
        return obj && typeof Symbol === "function" && obj.constructor === Symbol && obj !== Symbol.prototype ? "symbol" : typeof obj;
    };

    function _possibleConstructorReturn(self, call) {
        if (!self) {
            throw new ReferenceError("this hasn't been initialised - super() hasn't been called");
        }
        return call && (typeof call === "object" || typeof call === "function") ? call : self;
    }

    function _inherits(subClass, superClass) {
        if (typeof superClass !== "function" && superClass !== null) {
            throw new TypeError("Super expression must either be null or a function, not " + typeof superClass);
        }
        subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } });
        if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass;
    }

    function _toConsumableArray(arr) {
        if (Array.isArray(arr)) {
            for (var i = 0, arr2 = Array(arr.length); i < arr.length; i++) {
                arr2[i] = arr[i];
            }
            return arr2;
        } else {
            return Array.from(arr);
        }
    }

    function _classCallCheck(instance, Constructor) {
        if (!(instance instanceof Constructor)) {
            throw new TypeError("Cannot call a class as a function");
        }
    }

    var e = { ar: "٠١٢٣٤٥٦٧٨٩", fa: "۰۱۲۳۴۵۶۷۸۹", ml: "൦൧൨൩൪൫൬൭൮൯", kn: "೦೧೨೩೪೫೬೭೮೯", lo: "໐໑໒໓໔໕໖໗໘໙", or: "୦୧୨୩୪୫୬୭୮୯", kh: "០១២៣៤៥៦៧៨៩", nqo: "߀߁߂߃߄߅߆߇߈߉", pa: "੦੧੨੩੪੫੬੭੮੯", gu: "૦૧૨૩૪૫૬૭૮૯", hi: "०१२३४५६७८९", my: "၀၁၂၃၄၅၆၇၈၉", ta: "௦௧௨௩௪௫௬௭௮௯", te: "౦౧౨౩౪౫౬౭౮౯", th: "๐๑๒๓๔๕๖๗๘๙", bo: "༠༡༢༣༤༥༦༧༨༩" },
        u = {
            ab: [ "ru" ],
            abs: [ "id" ],
            ace: [ "id" ],
            ady: [ "ady-cyrl" ],
            aeb: [ "aeb-arab" ],
            "aeb-arab": [ "ar" ],
            aln: [ "sq" ],
            alt: [ "ru" ],
            ami: [ "zh-hant" ],
            an: [ "es" ],
            anp: [ "hi" ],
            arn: [ "es" ],
            arq: [ "ar" ],
            ary: [ "ar" ],
            arz: [ "ar" ],
            ast: [ "es" ],
            atj: [ "fr" ],
            av: [ "ru" ],
            avk: [ "fr", "es", "ru" ],
            awa: [ "hi" ],
            ay: [ "es" ],
            azb: [ "fa" ],
            ba: [ "ru" ],
            ban: [ "id" ],
            "ban-bali": [ "ban" ],
            bar: [ "de" ],
            bbc: [ "bbc-latn" ],
            "bbc-latn": [ "id" ],
            bcc: [ "fa" ],
            "be-tarask": [ "be" ],
            bgn: [ "fa" ],
            bh: [ "bho" ],
            bi: [ "en" ],
            bjn: [ "id" ],
            bm: [ "fr" ],
            bpy: [ "bn" ],
            bqi: [ "fa" ],
            br: [ "fr" ],
            btm: [ "id" ],
            bug: [ "id" ],
            bxr: [ "ru" ],
            ca: [ "oc" ],
            "cbk-zam": [ "es" ],
            cdo: [ "nan", "zh-hant" ],
            ce: [ "ru" ],
            co: [ "it" ],
            crh: [ "crh-latn" ],
            "crh-cyrl": [ "ru" ],
            cs: [ "sk" ],
            csb: [ "pl" ],
            cv: [ "ru" ],
            "de-at": [ "de" ],
            "de-ch": [ "de" ],
            "de-formal": [ "de" ],
            dsb: [ "hsb", "de" ],
            dtp: [ "ms" ],
            dty: [ "ne" ],
            egl: [ "it" ],
            eml: [ "it" ],
            "en-ca": [ "en" ],
            "en-gb": [ "en" ],
            "es-419": [ "es" ],
            "es-formal": [ "es" ],
            ext: [ "es" ],
            ff: [ "fr" ],
            fit: [ "fi" ],
            frc: [ "fr" ],
            frp: [ "fr" ],
            frr: [ "de" ],
            fur: [ "it" ],
            gag: [ "tr" ],
            gan: [ "gan-hant", "zh-hant", "zh-hans" ],
            "gan-hans": [ "zh-hans" ],
            "gan-hant": [ "zh-hant", "zh-hans" ],
            gcr: [ "fr" ],
            gl: [ "pt" ],
            glk: [ "fa" ],
            gn: [ "es" ],
            gom: [ "gom-deva" ],
            "gom-deva": [ "hi" ],
            gor: [ "id" ],
            gsw: [ "de" ],
            guc: [ "es" ],
            hak: [ "zh-hant" ],
            hif: [ "hif-latn" ],
            hrx: [ "de" ],
            hsb: [ "dsb", "de" ],
            ht: [ "fr" ],
            "hu-formal": [ "hu" ],
            hyw: [ "hy" ],
            ii: [ "zh-cn", "zh-hans" ],
            inh: [ "ru" ],
            io: [ "eo" ],
            iu: [ "ike-cans" ],
            jam: [ "en" ],
            jut: [ "da" ],
            jv: [ "id" ],
            kaa: [ "kk-latn", "kk-cyrl" ],
            kab: [ "fr" ],
            kbd: [ "kbd-cyrl" ],
            kbp: [ "fr" ],
            khw: [ "ur" ],
            kiu: [ "tr" ],
            kjp: [ "my" ],
            kk: [ "kk-cyrl" ],
            "kk-arab": [ "kk-cyrl" ],
            "kk-cn": [ "kk-arab", "kk-cyrl" ],
            "kk-kz": [ "kk-cyrl" ],
            "kk-latn": [ "kk-cyrl" ],
            "kk-tr": [ "kk-latn", "kk-cyrl" ],
            kl: [ "da" ],
            "ko-kp": [ "ko" ],
            koi: [ "ru" ],
            krc: [ "ru" ],
            krl: [ "fi" ],
            ks: [ "ks-arab" ],
            ksh: [ "de" ],
            ku: [ "ku-latn" ],
            "ku-arab": [ "ckb" ],
            kum: [ "ru" ],
            kv: [ "ru" ],
            lad: [ "es" ],
            lb: [ "de" ],
            lbe: [ "ru" ],
            lez: [ "ru", "az" ],
            li: [ "nl" ],
            lij: [ "it" ],
            liv: [ "et" ],
            lki: [ "fa" ],
            lld: [ "it", "rm", "fur" ],
            lmo: [ "pms", "eml", "lij", "vec", "it" ],
            ln: [ "fr" ],
            lrc: [ "fa" ],
            ltg: [ "lv" ],
            luz: [ "fa" ],
            lzh: [ "zh-hant" ],
            lzz: [ "tr" ],
            mad: [ "id" ],
            mai: [ "hi" ],
            "map-bms": [ "jv", "id" ],
            mdf: [ "myv", "ru" ],
            mg: [ "fr" ],
            mhr: [ "mrj", "ru" ],
            min: [ "id" ],
            mnw: [ "my" ],
            mo: [ "ro" ],
            mrj: [ "mhr", "ru" ],
            "ms-arab": [ "ms" ],
            mwl: [ "pt" ],
            myv: [ "mdf", "ru" ],
            mzn: [ "fa" ],
            nah: [ "es" ],
            nan: [ "cdo", "zh-hant" ],
            nap: [ "it" ],
            nb: [ "nn" ],
            nds: [ "de" ],
            "nds-nl": [ "nl" ],
            nia: [ "id" ],
            "nl-informal": [ "nl" ],
            nn: [ "nb" ],
            nrm: [ "fr" ],
            oc: [ "ca", "fr" ],
            olo: [ "fi" ],
            os: [ "ru" ],
            pcd: [ "fr" ],
            pdc: [ "de" ],
            pdt: [ "de" ],
            pfl: [ "de" ],
            pih: [ "en" ],
            pms: [ "it" ],
            pnt: [ "el" ],
            pt: [ "pt-br" ],
            "pt-br": [ "pt" ],
            qu: [ "qug", "es" ],
            qug: [ "qu", "es" ],
            rgn: [ "it" ],
            rmy: [ "ro" ],
            "roa-tara": [ "it" ],
            rue: [ "uk", "ru" ],
            rup: [ "ro" ],
            ruq: [ "ruq-latn", "ro" ],
            "ruq-cyrl": [ "mk" ],
            "ruq-latn": [ "ro" ],
            sa: [ "hi" ],
            sah: [ "ru" ],
            scn: [ "it" ],
            sco: [ "en" ],
            sdc: [ "it" ],
            sdh: [ "cbk", "fa" ],
            ses: [ "fr" ],
            sg: [ "fr" ],
            sgs: [ "lt" ],
            sh: [ "bs", "sr-el", "hr" ],
            shi: [ "fr" ],
            shy: [ "shy-latn" ],
            "shy-latn": [ "fr" ],
            sk: [ "cs" ],
            skr: [ "skr-arab" ],
            "skr-arab": [ "ur", "pnb" ],
            sli: [ "de" ],
            smn: [ "fi" ],
            sr: [ "sr-ec" ],
            srn: [ "nl" ],
            stq: [ "de" ],
            sty: [ "ru" ],
            su: [ "id" ],
            szl: [ "pl" ],
            szy: [ "zh-tw", "zh-hant", "zh-hans" ],
            tay: [ "zh-tw", "zh-hant", "zh-hans" ],
            tcy: [ "kn" ],
            tet: [ "pt" ],
            tg: [ "tg-cyrl" ],
            trv: [ "zh-tw", "zh-hant", "zh-hans" ],
            tt: [ "tt-cyrl", "ru" ],
            "tt-cyrl": [ "ru" ],
            ty: [ "fr" ],
            tyv: [ "ru" ],
            udm: [ "ru" ],
            ug: [ "ug-arab" ],
            vec: [ "it" ],
            vep: [ "et" ],
            vls: [ "nl" ],
            vmf: [ "de" ],
            vot: [ "fi" ],
            vro: [ "et" ],
            wa: [ "fr" ],
            wo: [ "fr" ],
            wuu: [ "zh-hans" ],
            xal: [ "ru" ],
            xmf: [ "ka" ],
            yi: [ "he" ],
            za: [ "zh-hans" ],
            zea: [ "nl" ],
            zgh: [ "kab" ],
            zh: [ "zh-hans" ],
            "zh-cn": [ "zh-hans" ],
            "zh-hant": [ "zh-hans" ],
            "zh-hk": [ "zh-hant", "zh-hans" ],
            "zh-mo": [ "zh-hk", "zh-hant", "zh-hans" ],
            "zh-my": [ "zh-sg", "zh-hans" ],
            "zh-sg": [ "zh-hans" ],
            "zh-tw": [ "zh-hant", "zh-hans" ],
            "default": [ "default" ]
        };
    var d = function () {
        function d(e) {
            _classCallCheck(this, d);

            this.locale = e;
        }

        _createClass(d, [ {
            key: "convertPlural",
            value: function convertPlural(e, u) {
                var d = /\d+=/i;
                if (!u || 0 === u.length) return "";
                for (var _t = 0; _t < u.length; _t++) {
                    var _n = u[_t];
                    if (d.test(_n)) {
                        if (parseInt(_n.slice(0, _n.indexOf("=")), 10) === e) return _n.slice(_n.indexOf("=") + 1);
                        u[_t] = void 0;
                    }
                }
                u = u.filter(function (e) {
                    return !!e;
                });
                var t = this.getPluralForm(e, this.locale);
                return t = Math.min(t, u.length - 1), u[t];
            }
        }, {
            key: "getPluralForm",
            value: function getPluralForm(e, u) {
                var d = new Intl.PluralRules(u),
                    t = d.resolvedOptions().pluralCategories,
                    n = d.select(e);
                return [ "zero", "one", "two", "few", "many", "other" ].filter(function (e) {
                    return t.includes(e);
                }).indexOf(n);
            }
        }, {
            key: "convertNumber",
            value: function convertNumber(e) {
                var d = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : !1;
                var t = this.digitTransformTable(this.locale),
                    n = "";
                if (d) {
                    if (parseFloat(e, 10) === e || !t) return e;
                    var _u = [];
                    for (var _e in t) {
                        _u[t[_e]] = _e;
                    }
                    t = _u;
                    var _d = String(e);
                    for (var _e2 = 0; _e2 < _d.length; _e2++) {
                        n += t[_d[_e2]] || _d[_e2];
                    }
                    return parseFloat(n, 10);
                }
                if (Intl.NumberFormat) {
                    var _d2 = void 0;
                    var _t2 = [].concat(_toConsumableArray(u[this.locale] || []), [ "en" ]);
                    return _d2 = Intl.NumberFormat.supportedLocalesOf(this.locale).length ? [ this.locale ] : _t2, n = new Intl.NumberFormat(_d2).format(e), "NaN" === n && (n = e), n;
                }
            }
        }, {
            key: "convertGrammar",
            value: function convertGrammar(e, u) {
                return e;
            }
        }, {
            key: "gender",
            value: function gender(e, u) {
                if (!u || 0 === u.length) return "";
                for (; u.length < 2;) {
                    u.push(u[u.length - 1]);
                }
                return "male" === e ? u[0] : "female" === e ? u[1] : 3 === u.length ? u[2] : u[0];
            }
        }, {
            key: "digitTransformTable",
            value: function digitTransformTable(u) {
                return !!e[u] && e[u].split("");
            }
        } ]);

        return d;
    }();

    var t = {
        bs: function (_d3) {
            _inherits(bs, _d3);

            function bs() {
                _classCallCheck(this, bs);

                return _possibleConstructorReturn(this, (bs.__proto__ || Object.getPrototypeOf(bs)).apply(this, arguments));
            }

            _createClass(bs, [ {
                key: "convertGrammar",
                value: function convertGrammar(e, u) {
                    switch (u) {
                        case "instrumental":
                            e = "s " + e;
                            break;
                        case "lokativ":
                            e = "o " + e;
                    }
                    return e;
                }
            } ]);

            return bs;
        }(d), default: d, dsb: function (_d4) {
            _inherits(dsb, _d4);

            function dsb() {
                _classCallCheck(this, dsb);

                return _possibleConstructorReturn(this, (dsb.__proto__ || Object.getPrototypeOf(dsb)).apply(this, arguments));
            }

            _createClass(dsb, [ {
                key: "convertGrammar",
                value: function convertGrammar(e, u) {
                    switch (u) {
                        case "instrumental":
                            e = "z " + e;
                            break;
                        case "lokatiw":
                            e = "wo " + e;
                    }
                    return e;
                }
            } ]);

            return dsb;
        }(d), fi: function (_d5) {
            _inherits(fi, _d5);

            function fi() {
                _classCallCheck(this, fi);

                return _possibleConstructorReturn(this, (fi.__proto__ || Object.getPrototypeOf(fi)).apply(this, arguments));
            }

            _createClass(fi, [ {
                key: "convertGrammar",
                value: function convertGrammar(e, u) {
                    var d = e.match(/[aou][^äöy]*$/i);
                    var t = e;
                    switch (e.match(/wiki$/i) && (d = !1), e.match(/[bcdfghjklmnpqrstvwxz]$/i) && (e += "i"), u) {
                        case "genitive":
                            e += "n";
                            break;
                        case "elative":
                            e += d ? "sta" : "stä";
                            break;
                        case "partitive":
                            e += d ? "a" : "ä";
                            break;
                        case "illative":
                            e += e.slice(-1) + "n";
                            break;
                        case "inessive":
                            e += d ? "ssa" : "ssä";
                            break;
                        default:
                            e = t;
                    }
                    return e;
                }
            } ]);

            return fi;
        }(d), ga: function (_d6) {
            _inherits(ga, _d6);

            function ga() {
                _classCallCheck(this, ga);

                return _possibleConstructorReturn(this, (ga.__proto__ || Object.getPrototypeOf(ga)).apply(this, arguments));
            }

            _createClass(ga, [ {
                key: "convertGrammar",
                value: function convertGrammar(e, u) {
                    if ("ainmlae" === u) switch (e) {
                        case "an Domhnach":
                            e = "Dé Domhnaigh";
                            break;
                        case "an Luan":
                            e = "Dé Luain";
                            break;
                        case "an Mháirt":
                            e = "Dé Mháirt";
                            break;
                        case "an Chéadaoin":
                            e = "Dé Chéadaoin";
                            break;
                        case "an Déardaoin":
                            e = "Déardaoin";
                            break;
                        case "an Aoine":
                            e = "Dé hAoine";
                            break;
                        case "an Satharn":
                            e = "Dé Sathairn";
                    }
                    return e;
                }
            } ]);

            return ga;
        }(d), he: function (_d7) {
            _inherits(he, _d7);

            function he() {
                _classCallCheck(this, he);

                return _possibleConstructorReturn(this, (he.__proto__ || Object.getPrototypeOf(he)).apply(this, arguments));
            }

            _createClass(he, [ {
                key: "convertGrammar",
                value: function convertGrammar(e, u) {
                    switch (u) {
                        case "prefixed":
                        case "תחילית":
                            "ו" === e.slice(0, 1) && "וו" !== e.slice(0, 2) && (e = "ו" + e), "ה" === e.slice(0, 1) && (e = e.slice(1)), (e.slice(0, 1) < "א" || e.slice(0, 1) > "ת") && (e = "־" + e);
                    }
                    return e;
                }
            } ]);

            return he;
        }(d), hsb: function (_d8) {
            _inherits(hsb, _d8);

            function hsb() {
                _classCallCheck(this, hsb);

                return _possibleConstructorReturn(this, (hsb.__proto__ || Object.getPrototypeOf(hsb)).apply(this, arguments));
            }

            _createClass(hsb, [ {
                key: "convertGrammar",
                value: function convertGrammar(e, u) {
                    switch (u) {
                        case "instrumental":
                            e = "z " + e;
                            break;
                        case "lokatiw":
                            e = "wo " + e;
                    }
                    return e;
                }
            } ]);

            return hsb;
        }(d), hu: function (_d9) {
            _inherits(hu, _d9);

            function hu() {
                _classCallCheck(this, hu);

                return _possibleConstructorReturn(this, (hu.__proto__ || Object.getPrototypeOf(hu)).apply(this, arguments));
            }

            _createClass(hu, [ {
                key: "convertGrammar",
                value: function convertGrammar(e, u) {
                    switch (u) {
                        case "rol":
                            e += "ról";
                            break;
                        case "ba":
                            e += "ba";
                            break;
                        case "k":
                            e += "k";
                    }
                    return e;
                }
            } ]);

            return hu;
        }(d), hy: function (_d10) {
            _inherits(hy, _d10);

            function hy() {
                _classCallCheck(this, hy);

                return _possibleConstructorReturn(this, (hy.__proto__ || Object.getPrototypeOf(hy)).apply(this, arguments));
            }

            _createClass(hy, [ {
                key: "convertGrammar",
                value: function convertGrammar(e, u) {
                    return "genitive" === u && ("ա" === e.slice(-1) ? e = e.slice(0, -1) + "այի" : "ո" === e.slice(-1) ? e = e.slice(0, -1) + "ոյի" : "գիրք" === e.slice(-4) ? e = e.slice(0, -4) + "գրքի" : e += "ի"), e;
                }
            } ]);

            return hy;
        }(d), la: function (_d11) {
            _inherits(la, _d11);

            function la() {
                _classCallCheck(this, la);

                return _possibleConstructorReturn(this, (la.__proto__ || Object.getPrototypeOf(la)).apply(this, arguments));
            }

            _createClass(la, [ {
                key: "convertGrammar",
                value: function convertGrammar(e, u) {
                    switch (u) {
                        case "genitive":
                            e = (e = (e = (e = (e = (e = (e = (e = (e = e.replace(/u[ms]$/i, "i")).replace(/ommunia$/i, "ommunium")).replace(/a$/i, "ae")).replace(/libri$/i, "librorum")).replace(/nuntii$/i, "nuntiorum")).replace(/tio$/i, "tionis")).replace(/ns$/i, "ntis")).replace(/as$/i, "atis")).replace(/es$/i, "ei");
                            break;
                        case "accusative":
                            e = (e = (e = (e = (e = (e = (e = (e = (e = e.replace(/u[ms]$/i, "um")).replace(/ommunia$/i, "am")).replace(/a$/i, "ommunia")).replace(/libri$/i, "libros")).replace(/nuntii$/i, "nuntios")).replace(/tio$/i, "tionem")).replace(/ns$/i, "ntem")).replace(/as$/i, "atem")).replace(/es$/i, "em");
                            break;
                        case "ablative":
                            e = (e = (e = (e = (e = (e = (e = (e = (e = e.replace(/u[ms]$/i, "o")).replace(/ommunia$/i, "ommunibus")).replace(/a$/i, "a")).replace(/libri$/i, "libris")).replace(/nuntii$/i, "nuntiis")).replace(/tio$/i, "tione")).replace(/ns$/i, "nte")).replace(/as$/i, "ate")).replace(/es$/i, "e");
                    }
                    return e;
                }
            } ]);

            return la;
        }(d), os: function (_d12) {
            _inherits(os, _d12);

            function os() {
                _classCallCheck(this, os);

                return _possibleConstructorReturn(this, (os.__proto__ || Object.getPrototypeOf(os)).apply(this, arguments));
            }

            _createClass(os, [ {
                key: "convertGrammar",
                value: function convertGrammar(e, u) {
                    var d = void 0,
                        t = void 0,
                        n = void 0,
                        r = void 0;
                    switch (d = "мæ", t = "", n = "", r = "", e.match(/тæ$/i) ? (e = e.slice(0, -1), d = "æм") : e.match(/[аæеёиоыэюя]$/i) ? t = "й" : e.match(/у$/i) ? e.slice(-2, -1).match(/[аæеёиоыэюя]$/i) || (t = "й") : e.match(/[бвгджзйклмнопрстфхцчшщьъ]$/i) || (n = "-"), u) {
                        case "genitive":
                            r = n + t + "ы";
                            break;
                        case "dative":
                            r = n + t + "æн";
                            break;
                        case "allative":
                            r = n + d;
                            break;
                        case "ablative":
                            r = "й" === t ? n + t + "æ" : n + t + "æй";
                            break;
                        case "superessive":
                            r = n + t + "ыл";
                            break;
                        case "equative":
                            r = n + t + "ау";
                            break;
                        case "comitative":
                            r = n + "имæ";
                    }
                    return e + r;
                }
            } ]);

            return os;
        }(d), ru: function (_d13) {
            _inherits(ru, _d13);

            function ru() {
                _classCallCheck(this, ru);

                return _possibleConstructorReturn(this, (ru.__proto__ || Object.getPrototypeOf(ru)).apply(this, arguments));
            }

            _createClass(ru, [ {
                key: "convertGrammar",
                value: function convertGrammar(e, u) {
                    return "genitive" === u && ("ь" === e.slice(-1) ? e = e.slice(0, -1) + "я" : "ия" === e.slice(-2) ? e = e.slice(0, -2) + "ии" : "ка" === e.slice(-2) ? e = e.slice(0, -2) + "ки" : "ти" === e.slice(-2) ? e = e.slice(0, -2) + "тей" : "ды" === e.slice(-2) ? e = e.slice(0, -2) + "дов" : "ник" === e.slice(-3) && (e = e.slice(0, -3) + "ника")), e;
                }
            } ]);

            return ru;
        }(d), sl: function (_d14) {
            _inherits(sl, _d14);

            function sl() {
                _classCallCheck(this, sl);

                return _possibleConstructorReturn(this, (sl.__proto__ || Object.getPrototypeOf(sl)).apply(this, arguments));
            }

            _createClass(sl, [ {
                key: "convertGrammar",
                value: function convertGrammar(e, u) {
                    switch (u) {
                        case "mestnik":
                            e = "o " + e;
                            break;
                        case "orodnik":
                            e = "z " + e;
                    }
                    return e;
                }
            } ]);

            return sl;
        }(d), uk: function (_d15) {
            _inherits(uk, _d15);

            function uk() {
                _classCallCheck(this, uk);

                return _possibleConstructorReturn(this, (uk.__proto__ || Object.getPrototypeOf(uk)).apply(this, arguments));
            }

            _createClass(uk, [ {
                key: "convertGrammar",
                value: function convertGrammar(e, u) {
                    switch (u) {
                        case "genitive":
                            "ь" === e.slice(-1) ? e = e.slice(0, -1) + "я" : "ія" === e.slice(-2) ? e = e.slice(0, -2) + "ії" : "ка" === e.slice(-2) ? e = e.slice(0, -2) + "ки" : "ти" === e.slice(-2) ? e = e.slice(0, -2) + "тей" : "ды" === e.slice(-2) ? e = e.slice(0, -2) + "дов" : "ник" === e.slice(-3) && (e = e.slice(0, -3) + "ника");
                            break;
                        case "accusative":
                            "ія" === e.slice(-2) && (e = e.slice(0, -2) + "ію");
                    }
                    return e;
                }
            } ]);

            return uk;
        }(d)
    };
    var n = new RegExp("(?:([A-Za-z\xAA\xB5\xBA\xC0-\xD6\xD8-\xF6\xF8-\u02B8\u02BB-\u02C1\u02D0\u02D1\u02E0-\u02E4\u02EE\u0370-\u0373\u0376\u0377\u037A-\u037D\u037F\u0386\u0388-\u038A\u038C\u038E-\u03A1\u03A3-\u03F5\u03F7-\u0482\u048A-\u052F\u0531-\u0556\u0559-\u055F\u0561-\u0587\u0589\u0903-\u0939\u093B\u093D-\u0940\u0949-\u094C\u094E-\u0950\u0958-\u0961\u0964-\u0980\u0982\u0983\u0985-\u098C\u098F\u0990\u0993-\u09A8\u09AA-\u09B0\u09B2\u09B6-\u09B9\u09BD-\u09C0\u09C7\u09C8\u09CB\u09CC\u09CE\u09D7\u09DC\u09DD\u09DF-\u09E1\u09E6-\u09F1\u09F4-\u09FA\u0A03\u0A05-\u0A0A\u0A0F\u0A10\u0A13-\u0A28\u0A2A-\u0A30\u0A32\u0A33\u0A35\u0A36\u0A38\u0A39\u0A3E-\u0A40\u0A59-\u0A5C\u0A5E\u0A66-\u0A6F\u0A72-\u0A74\u0A83\u0A85-\u0A8D\u0A8F-\u0A91\u0A93-\u0AA8\u0AAA-\u0AB0\u0AB2\u0AB3\u0AB5-\u0AB9\u0ABD-\u0AC0\u0AC9\u0ACB\u0ACC\u0AD0\u0AE0\u0AE1\u0AE6-\u0AF0\u0AF9\u0B02\u0B03\u0B05-\u0B0C\u0B0F\u0B10\u0B13-\u0B28\u0B2A-\u0B30\u0B32\u0B33\u0B35-\u0B39\u0B3D\u0B3E\u0B40\u0B47\u0B48\u0B4B\u0B4C\u0B57\u0B5C\u0B5D\u0B5F-\u0B61\u0B66-\u0B77\u0B83\u0B85-\u0B8A\u0B8E-\u0B90\u0B92-\u0B95\u0B99\u0B9A\u0B9C\u0B9E\u0B9F\u0BA3\u0BA4\u0BA8-\u0BAA\u0BAE-\u0BB9\u0BBE\u0BBF\u0BC1\u0BC2\u0BC6-\u0BC8\u0BCA-\u0BCC\u0BD0\u0BD7\u0BE6-\u0BF2\u0C01-\u0C03\u0C05-\u0C0C\u0C0E-\u0C10\u0C12-\u0C28\u0C2A-\u0C39\u0C3D\u0C41-\u0C44\u0C58-\u0C5A\u0C60\u0C61\u0C66-\u0C6F\u0C7F\u0C82\u0C83\u0C85-\u0C8C\u0C8E-\u0C90\u0C92-\u0CA8\u0CAA-\u0CB3\u0CB5-\u0CB9\u0CBD-\u0CC4\u0CC6-\u0CC8\u0CCA\u0CCB\u0CD5\u0CD6\u0CDE\u0CE0\u0CE1\u0CE6-\u0CEF\u0CF1\u0CF2\u0D02\u0D03\u0D05-\u0D0C\u0D0E-\u0D10\u0D12-\u0D3A\u0D3D-\u0D40\u0D46-\u0D48\u0D4A-\u0D4C\u0D4E\u0D57\u0D5F-\u0D61\u0D66-\u0D75\u0D79-\u0D7F\u0D82\u0D83\u0D85-\u0D96\u0D9A-\u0DB1\u0DB3-\u0DBB\u0DBD\u0DC0-\u0DC6\u0DCF-\u0DD1\u0DD8-\u0DDF\u0DE6-\u0DEF\u0DF2-\u0DF4\u0E01-\u0E30\u0E32\u0E33\u0E40-\u0E46\u0E4F-\u0E5B\u0E81\u0E82\u0E84\u0E87\u0E88\u0E8A\u0E8D\u0E94-\u0E97\u0E99-\u0E9F\u0EA1-\u0EA3\u0EA5\u0EA7\u0EAA\u0EAB\u0EAD-\u0EB0\u0EB2\u0EB3\u0EBD\u0EC0-\u0EC4\u0EC6\u0ED0-\u0ED9\u0EDC-\u0EDF\u0F00-\u0F17\u0F1A-\u0F34\u0F36\u0F38\u0F3E-\u0F47\u0F49-\u0F6C\u0F7F\u0F85\u0F88-\u0F8C\u0FBE-\u0FC5\u0FC7-\u0FCC\u0FCE-\u0FDA\u1000-\u102C\u1031\u1038\u103B\u103C\u103F-\u1057\u105A-\u105D\u1061-\u1070\u1075-\u1081\u1083\u1084\u1087-\u108C\u108E-\u109C\u109E-\u10C5\u10C7\u10CD\u10D0-\u1248\u124A-\u124D\u1250-\u1256\u1258\u125A-\u125D\u1260-\u1288\u128A-\u128D\u1290-\u12B0\u12B2-\u12B5\u12B8-\u12BE\u12C0\u12C2-\u12C5\u12C8-\u12D6\u12D8-\u1310\u1312-\u1315\u1318-\u135A\u1360-\u137C\u1380-\u138F\u13A0-\u13F5\u13F8-\u13FD\u1401-\u167F\u1681-\u169A\u16A0-\u16F8\u1700-\u170C\u170E-\u1711\u1720-\u1731\u1735\u1736\u1740-\u1751\u1760-\u176C\u176E-\u1770\u1780-\u17B3\u17B6\u17BE-\u17C5\u17C7\u17C8\u17D4-\u17DA\u17DC\u17E0-\u17E9\u1810-\u1819\u1820-\u1877\u1880-\u18A8\u18AA\u18B0-\u18F5\u1900-\u191E\u1923-\u1926\u1929-\u192B\u1930\u1931\u1933-\u1938\u1946-\u196D\u1970-\u1974\u1980-\u19AB\u19B0-\u19C9\u19D0-\u19DA\u1A00-\u1A16\u1A19\u1A1A\u1A1E-\u1A55\u1A57\u1A61\u1A63\u1A64\u1A6D-\u1A72\u1A80-\u1A89\u1A90-\u1A99\u1AA0-\u1AAD\u1B04-\u1B33\u1B35\u1B3B\u1B3D-\u1B41\u1B43-\u1B4B\u1B50-\u1B6A\u1B74-\u1B7C\u1B82-\u1BA1\u1BA6\u1BA7\u1BAA\u1BAE-\u1BE5\u1BE7\u1BEA-\u1BEC\u1BEE\u1BF2\u1BF3\u1BFC-\u1C2B\u1C34\u1C35\u1C3B-\u1C49\u1C4D-\u1C7F\u1CC0-\u1CC7\u1CD3\u1CE1\u1CE9-\u1CEC\u1CEE-\u1CF3\u1CF5\u1CF6\u1D00-\u1DBF\u1E00-\u1F15\u1F18-\u1F1D\u1F20-\u1F45\u1F48-\u1F4D\u1F50-\u1F57\u1F59\u1F5B\u1F5D\u1F5F-\u1F7D\u1F80-\u1FB4\u1FB6-\u1FBC\u1FBE\u1FC2-\u1FC4\u1FC6-\u1FCC\u1FD0-\u1FD3\u1FD6-\u1FDB\u1FE0-\u1FEC\u1FF2-\u1FF4\u1FF6-\u1FFC\u200E\u2071\u207F\u2090-\u209C\u2102\u2107\u210A-\u2113\u2115\u2119-\u211D\u2124\u2126\u2128\u212A-\u212D\u212F-\u2139\u213C-\u213F\u2145-\u2149\u214E\u214F\u2160-\u2188\u2336-\u237A\u2395\u249C-\u24E9\u26AC\u2800-\u28FF\u2C00-\u2C2E\u2C30-\u2C5E\u2C60-\u2CE4\u2CEB-\u2CEE\u2CF2\u2CF3\u2D00-\u2D25\u2D27\u2D2D\u2D30-\u2D67\u2D6F\u2D70\u2D80-\u2D96\u2DA0-\u2DA6\u2DA8-\u2DAE\u2DB0-\u2DB6\u2DB8-\u2DBE\u2DC0-\u2DC6\u2DC8-\u2DCE\u2DD0-\u2DD6\u2DD8-\u2DDE\u3005-\u3007\u3021-\u3029\u302E\u302F\u3031-\u3035\u3038-\u303C\u3041-\u3096\u309D-\u309F\u30A1-\u30FA\u30FC-\u30FF\u3105-\u312D\u3131-\u318E\u3190-\u31BA\u31F0-\u321C\u3220-\u324F\u3260-\u327B\u327F-\u32B0\u32C0-\u32CB\u32D0-\u32FE\u3300-\u3376\u337B-\u33DD\u33E0-\u33FE\u3400-\u4DB5\u4E00-\u9FD5\uA000-\uA48C\uA4D0-\uA60C\uA610-\uA62B\uA640-\uA66E\uA680-\uA69D\uA6A0-\uA6EF\uA6F2-\uA6F7\uA722-\uA787\uA789-\uA7AD\uA7B0-\uA7B7\uA7F7-\uA801\uA803-\uA805\uA807-\uA80A\uA80C-\uA824\uA827\uA830-\uA837\uA840-\uA873\uA880-\uA8C3\uA8CE-\uA8D9\uA8F2-\uA8FD\uA900-\uA925\uA92E-\uA946\uA952\uA953\uA95F-\uA97C\uA983-\uA9B2\uA9B4\uA9B5\uA9BA\uA9BB\uA9BD-\uA9CD\uA9CF-\uA9D9\uA9DE-\uA9E4\uA9E6-\uA9FE\uAA00-\uAA28\uAA2F\uAA30\uAA33\uAA34\uAA40-\uAA42\uAA44-\uAA4B\uAA4D\uAA50-\uAA59\uAA5C-\uAA7B\uAA7D-\uAAAF\uAAB1\uAAB5\uAAB6\uAAB9-\uAABD\uAAC0\uAAC2\uAADB-\uAAEB\uAAEE-\uAAF5\uAB01-\uAB06\uAB09-\uAB0E\uAB11-\uAB16\uAB20-\uAB26\uAB28-\uAB2E\uAB30-\uAB65\uAB70-\uABE4\uABE6\uABE7\uABE9-\uABEC\uABF0-\uABF9\uAC00-\uD7A3\uD7B0-\uD7C6\uD7CB-\uD7FB\uE000-\uFA6D\uFA70-\uFAD9\uFB00-\uFB06\uFB13-\uFB17\uFF21-\uFF3A\uFF41-\uFF5A\uFF66-\uFFBE\uFFC2-\uFFC7\uFFCA-\uFFCF\uFFD2-\uFFD7\uFFDA-\uFFDC]|\uD800[\uDC00-\uDC0B]|\uD800[\uDC0D-\uDC26]|\uD800[\uDC28-\uDC3A]|\uD800\uDC3C|\uD800\uDC3D|\uD800[\uDC3F-\uDC4D]|\uD800[\uDC50-\uDC5D]|\uD800[\uDC80-\uDCFA]|\uD800\uDD00|\uD800\uDD02|\uD800[\uDD07-\uDD33]|\uD800[\uDD37-\uDD3F]|\uD800[\uDDD0-\uDDFC]|\uD800[\uDE80-\uDE9C]|\uD800[\uDEA0-\uDED0]|\uD800[\uDF00-\uDF23]|\uD800[\uDF30-\uDF4A]|\uD800[\uDF50-\uDF75]|\uD800[\uDF80-\uDF9D]|\uD800[\uDF9F-\uDFC3]|\uD800[\uDFC8-\uDFD5]|\uD801[\uDC00-\uDC9D]|\uD801[\uDCA0-\uDCA9]|\uD801[\uDD00-\uDD27]|\uD801[\uDD30-\uDD63]|\uD801\uDD6F|\uD801[\uDE00-\uDF36]|\uD801[\uDF40-\uDF55]|\uD801[\uDF60-\uDF67]|\uD804\uDC00|\uD804[\uDC02-\uDC37]|\uD804[\uDC47-\uDC4D]|\uD804[\uDC66-\uDC6F]|\uD804[\uDC82-\uDCB2]|\uD804\uDCB7|\uD804\uDCB8|\uD804[\uDCBB-\uDCC1]|\uD804[\uDCD0-\uDCE8]|\uD804[\uDCF0-\uDCF9]|\uD804[\uDD03-\uDD26]|\uD804\uDD2C|\uD804[\uDD36-\uDD43]|\uD804[\uDD50-\uDD72]|\uD804[\uDD74-\uDD76]|\uD804[\uDD82-\uDDB5]|\uD804[\uDDBF-\uDDC9]|\uD804\uDDCD|\uD804[\uDDD0-\uDDDF]|\uD804[\uDDE1-\uDDF4]|\uD804[\uDE00-\uDE11]|\uD804[\uDE13-\uDE2E]|\uD804\uDE32|\uD804\uDE33|\uD804\uDE35|\uD804[\uDE38-\uDE3D]|\uD804[\uDE80-\uDE86]|\uD804\uDE88|\uD804[\uDE8A-\uDE8D]|\uD804[\uDE8F-\uDE9D]|\uD804[\uDE9F-\uDEA9]|\uD804[\uDEB0-\uDEDE]|\uD804[\uDEE0-\uDEE2]|\uD804[\uDEF0-\uDEF9]|\uD804\uDF02|\uD804\uDF03|\uD804[\uDF05-\uDF0C]|\uD804\uDF0F|\uD804\uDF10|\uD804[\uDF13-\uDF28]|\uD804[\uDF2A-\uDF30]|\uD804\uDF32|\uD804\uDF33|\uD804[\uDF35-\uDF39]|\uD804[\uDF3D-\uDF3F]|\uD804[\uDF41-\uDF44]|\uD804\uDF47|\uD804\uDF48|\uD804[\uDF4B-\uDF4D]|\uD804\uDF50|\uD804\uDF57|\uD804[\uDF5D-\uDF63]|\uD805[\uDC80-\uDCB2]|\uD805\uDCB9|\uD805[\uDCBB-\uDCBE]|\uD805\uDCC1|\uD805[\uDCC4-\uDCC7]|\uD805[\uDCD0-\uDCD9]|\uD805[\uDD80-\uDDB1]|\uD805[\uDDB8-\uDDBB]|\uD805\uDDBE|\uD805[\uDDC1-\uDDDB]|\uD805[\uDE00-\uDE32]|\uD805\uDE3B|\uD805\uDE3C|\uD805\uDE3E|\uD805[\uDE41-\uDE44]|\uD805[\uDE50-\uDE59]|\uD805[\uDE80-\uDEAA]|\uD805\uDEAC|\uD805\uDEAE|\uD805\uDEAF|\uD805\uDEB6|\uD805[\uDEC0-\uDEC9]|\uD805[\uDF00-\uDF19]|\uD805\uDF20|\uD805\uDF21|\uD805\uDF26|\uD805[\uDF30-\uDF3F]|\uD806[\uDCA0-\uDCF2]|\uD806\uDCFF|\uD806[\uDEC0-\uDEF8]|\uD808[\uDC00-\uDF99]|\uD809[\uDC00-\uDC6E]|\uD809[\uDC70-\uDC74]|\uD809[\uDC80-\uDD43]|\uD80C[\uDC00-\uDFFF]|\uD80D[\uDC00-\uDC2E]|\uD811[\uDC00-\uDE46]|\uD81A[\uDC00-\uDE38]|\uD81A[\uDE40-\uDE5E]|\uD81A[\uDE60-\uDE69]|\uD81A\uDE6E|\uD81A\uDE6F|\uD81A[\uDED0-\uDEED]|\uD81A\uDEF5|\uD81A[\uDF00-\uDF2F]|\uD81A[\uDF37-\uDF45]|\uD81A[\uDF50-\uDF59]|\uD81A[\uDF5B-\uDF61]|\uD81A[\uDF63-\uDF77]|\uD81A[\uDF7D-\uDF8F]|\uD81B[\uDF00-\uDF44]|\uD81B[\uDF50-\uDF7E]|\uD81B[\uDF93-\uDF9F]|\uD82C\uDC00|\uD82C\uDC01|\uD82F[\uDC00-\uDC6A]|\uD82F[\uDC70-\uDC7C]|\uD82F[\uDC80-\uDC88]|\uD82F[\uDC90-\uDC99]|\uD82F\uDC9C|\uD82F\uDC9F|\uD834[\uDC00-\uDCF5]|\uD834[\uDD00-\uDD26]|\uD834[\uDD29-\uDD66]|\uD834[\uDD6A-\uDD72]|\uD834\uDD83|\uD834\uDD84|\uD834[\uDD8C-\uDDA9]|\uD834[\uDDAE-\uDDE8]|\uD834[\uDF60-\uDF71]|\uD835[\uDC00-\uDC54]|\uD835[\uDC56-\uDC9C]|\uD835\uDC9E|\uD835\uDC9F|\uD835\uDCA2|\uD835\uDCA5|\uD835\uDCA6|\uD835[\uDCA9-\uDCAC]|\uD835[\uDCAE-\uDCB9]|\uD835\uDCBB|\uD835[\uDCBD-\uDCC3]|\uD835[\uDCC5-\uDD05]|\uD835[\uDD07-\uDD0A]|\uD835[\uDD0D-\uDD14]|\uD835[\uDD16-\uDD1C]|\uD835[\uDD1E-\uDD39]|\uD835[\uDD3B-\uDD3E]|\uD835[\uDD40-\uDD44]|\uD835\uDD46|\uD835[\uDD4A-\uDD50]|\uD835[\uDD52-\uDEA5]|\uD835[\uDEA8-\uDEDA]|\uD835[\uDEDC-\uDF14]|\uD835[\uDF16-\uDF4E]|\uD835[\uDF50-\uDF88]|\uD835[\uDF8A-\uDFC2]|\uD835[\uDFC4-\uDFCB]|\uD836[\uDC00-\uDDFF]|\uD836[\uDE37-\uDE3A]|\uD836[\uDE6D-\uDE74]|\uD836[\uDE76-\uDE83]|\uD836[\uDE85-\uDE8B]|\uD83C[\uDD10-\uDD2E]|\uD83C[\uDD30-\uDD69]|\uD83C[\uDD70-\uDD9A]|\uD83C[\uDDE6-\uDE02]|\uD83C[\uDE10-\uDE3A]|\uD83C[\uDE40-\uDE48]|\uD83C\uDE50|\uD83C\uDE51|[\uD840-\uD868][\uDC00-\uDFFF]|\uD869[\uDC00-\uDED6]|\uD869[\uDF00-\uDFFF]|[\uD86A-\uD86C][\uDC00-\uDFFF]|\uD86D[\uDC00-\uDF34]|\uD86D[\uDF40-\uDFFF]|\uD86E[\uDC00-\uDC1D]|\uD86E[\uDC20-\uDFFF]|[\uD86F-\uD872][\uDC00-\uDFFF]|\uD873[\uDC00-\uDEA1]|\uD87E[\uDC00-\uDE1D]|[\uDB80-\uDBBE][\uDC00-\uDFFF]|\uDBBF[\uDC00-\uDFFD]|[\uDBC0-\uDBFE][\uDC00-\uDFFF]|\uDBFF[\uDC00-\uDFFD])|([\u0590\u05BE\u05C0\u05C3\u05C6\u05C8-\u05FF\u07C0-\u07EA\u07F4\u07F5\u07FA-\u0815\u081A\u0824\u0828\u082E-\u0858\u085C-\u089F\u200F\uFB1D\uFB1F-\uFB28\uFB2A-\uFB4F\u0608\u060B\u060D\u061B-\u064A\u066D-\u066F\u0671-\u06D5\u06E5\u06E6\u06EE\u06EF\u06FA-\u0710\u0712-\u072F\u074B-\u07A5\u07B1-\u07BF\u08A0-\u08E2\uFB50-\uFD3D\uFD40-\uFDCF\uFDF0-\uFDFC\uFDFE\uFDFF\uFE70-\uFEFE]|\uD802[\uDC00-\uDD1E]|\uD802[\uDD20-\uDE00]|\uD802\uDE04|\uD802[\uDE07-\uDE0B]|\uD802[\uDE10-\uDE37]|\uD802[\uDE3B-\uDE3E]|\uD802[\uDE40-\uDEE4]|\uD802[\uDEE7-\uDF38]|\uD802[\uDF40-\uDFFF]|\uD803[\uDC00-\uDE5F]|\uD803[\uDE7F-\uDFFF]|\uD83A[\uDC00-\uDCCF]|\uD83A[\uDCD7-\uDFFF]|\uD83B[\uDC00-\uDDFF]|\uD83B[\uDF00-\uDFFF]|\uD83B[\uDF00-\uDFFF]|\uD83B[\uDF00-\uDFFF]|\uD83B[\uDF00-\uDFFF]|\uD83B[\uDF00-\uDFFF]|\uD83B[\uDF00-\uDFFF]|\uD83B[\uDF00-\uDFFF]|\uD83B[\uDF00-\uDFFF]|\uD83B[\uDF00-\uDFFF]|\uD83B[\uDF00-\uDFFF]|\uD83B[\uDF00-\uDFFF]|\uD83B[\uDF00-\uDFFF]|\uD83B[\uDF00-\uDFFF]|\uD83B[\uDE00-\uDEEF]|\uD83B[\uDEF2-\uDEFF]))");

    var r = function () {
        function r(e) {
            _classCallCheck(this, r);

            this.locale = e, this.language = new (t[e] || t.default)(e);
        }

        _createClass(r, [ {
            key: "emit",
            value: function emit(e, u) {
                var _this14 = this;

                var d = void 0,
                    t = void 0,
                    n = void 0;
                switch (typeof e === "undefined" ? "undefined" : _typeof(e)) {
                    case "string":
                    case "number":
                        d = e;
                        break;
                    case "object":
                        if (t = e.slice(1).map(function (e) {
                            return _this14.emit(e, u);
                        }), n = e[0].toLowerCase(), "function" != typeof this[n]) throw new Error('unknown operation "' + n + '"');
                        d = this[n](t, u);
                        break;
                    case "undefined":
                        d = "";
                        break;
                    default:
                        throw new Error("unexpected type in AST: " + (typeof e === "undefined" ? "undefined" : _typeof(e)));
                }
                return d;
            }
        }, {
            key: "concat",
            value: function concat(e) {
                var u = "";
                return e.forEach(function (e) {
                    u += e;
                }), u;
            }
        }, {
            key: "replace",
            value: function replace(e, u) {
                var d = parseInt(e[0], 10);
                return d < u.length ? u[d] : "$" + (d + 1);
            }
        }, {
            key: "plural",
            value: function plural(e) {
                var u = parseFloat(this.language.convertNumber(e[0], 10)),
                    d = e.slice(1);
                return d.length ? this.language.convertPlural(u, d) : "";
            }
        }, {
            key: "gender",
            value: function gender(e) {
                var u = e[0],
                    d = e.slice(1);
                return this.language.gender(u, d);
            }
        }, {
            key: "grammar",
            value: function grammar(e) {
                var u = e[0],
                    d = e[1];
                return d && u && this.language.convertGrammar(d, u);
            }
        }, {
            key: "wikilink",
            value: function wikilink(e) {
                var u = void 0,
                    d = e[0];
                ":" === d.charAt(0) && (d = d.slice(1));
                var t = "./" + d;
                return u = 1 === e.length ? d : e[1], "<a href=\"" + t + "\" title=\"" + d + "\">" + u + "</a>";
            }
        }, {
            key: "extlink",
            value: function extlink(e) {
                if (2 !== e.length) throw new Error("Expected two items in the node");
                return "<a href=\"" + e[0] + "\">" + e[1] + "</a>";
            }
        }, {
            key: "bidi",
            value: function bidi(e) {
                var u = function (e) {
                    var u = e.match(n);
                    return u ? void 0 === u[2] ? "ltr" : "rtl" : null;
                }(e[0]);
                return "ltr" === u ? "‪" + e[0] + "‬" : "rtl" === u ? "‫" + e[0] + "‬" : e[0];
            }
        }, {
            key: "formatnum",
            value: function formatnum(e) {
                var u = !!e[1] && "R" === e[1],
                    d = e[0];
                return "string" == typeof d || "number" == typeof d ? this.language.convertNumber(d, u) : d;
            }
        }, {
            key: "htmlattributes",
            value: function htmlattributes(e) {
                var u = {};
                for (var _d16 = 0, _t3 = e.length; _d16 < _t3; _d16 += 2) {
                    u[e[_d16]] = e[_d16 + 1];
                }
                return u;
            }
        }, {
            key: "htmlelement",
            value: function htmlelement(e) {
                var u = e.shift(),
                    d = e.shift();
                var t = e,
                    n = "";
                for (var _e3 in d) {
                    n += " " + _e3 + "=\"" + d[_e3] + "\"";
                }
                Array.isArray(t) || (t = [ t ]);
                return "<" + u + n + ">" + t.join("") + "</" + u + ">";
            }
        } ]);

        return r;
    }();

    var a = function () {
        function a(e) {
            var _ref = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {},
                _ref$wikilinks = _ref.wikilinks,
                u = _ref$wikilinks === undefined ? !1 : _ref$wikilinks;

            _classCallCheck(this, a);

            this.locale = e, this.wikilinks = u, this.emitter = new r(this.locale);
        }

        _createClass(a, [ {
            key: "parse",
            value: function parse(e, u) {
                if (e.includes("{{") || e.includes("<") || this.wikilinks && e.includes("[")) {
                    var _d17 = function (e) {
                        var _ref2 = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {},
                            _ref2$wikilinks = _ref2.wikilinks,
                            u = _ref2$wikilinks === undefined ? !1 : _ref2$wikilinks;

                        var d = 0;

                        function t(e) {
                            return function () {
                                for (var _u2 = 0; _u2 < e.length; _u2++) {
                                    var _d18 = e[_u2]();
                                    if (null !== _d18) return _d18;
                                }
                                return null;
                            };
                        }

                        function n(e) {
                            var u = d,
                                t = [];
                            for (var _n2 = 0; _n2 < e.length; _n2++) {
                                var _r = e[_n2]();
                                if (null === _r) return d = u, null;
                                t.push(_r);
                            }
                            return t;
                        }

                        function r(e, u) {
                            return function () {
                                var t = d,
                                    n = [];
                                var r = u();
                                for (; null !== r;) {
                                    n.push(r), r = u();
                                }
                                return n.length < e ? (d = t, null) : n;
                            };
                        }

                        function a(u) {
                            var t = u.length;
                            return function () {
                                var n = null;
                                return e.slice(d, d + t) === u && (n = u, d += t), n;
                            };
                        }

                        function c(u) {
                            return function () {
                                var t = e.slice(d).match(u);
                                return null === t ? null : (d += t[0].length, t[0]);
                            };
                        }

                        var s = c(/^\s+/),
                            l = a("|"),
                            i = a(":"),
                            o = a("\\"),
                            f = c(/^./),
                            h = a("$"),
                            b = c(/^\d+/),
                            m = a('"'),
                            k = a("'"),
                            p = c(u ? /^[^{}[\]$<\\]/ : /^[^{}$<\\]/),
                            g = c(u ? /^[^{}[\]$\\|]/ : /^[^{}$\\|]/),
                            w = t([ v, c(u ? /^[^{}[\]$\s]/ : /^[^{}$\s]/) ]);

                        function v() {
                            var e = n([ o, f ]);
                            return null === e ? null : e[1];
                        }

                        var y = t([ v, g ]),
                            z = t([ v, p ]);

                        function $() {
                            var e = n([ h, b ]);
                            return null === e ? null : [ "REPLACE", parseInt(e[1], 10) - 1 ];
                        }

                        var x = (C = c(/^[ !"$&'()*,./0-9;=?@A-Z^_`a-z~\x80-\xFF+-]+/), A = function A(e) {
                            return e.toString();
                        }, function () {
                            var e = C();
                            return null === e ? null : A(e);
                        });
                        var C, A;

                        function j() {
                            var e = n([ l, r(0, _) ]);
                            if (null === e) return null;
                            var u = e[1];
                            return u.length > 1 ? [ "CONCAT" ].concat(u) : u[0];
                        }

                        function T() {
                            var e = n([ x, i, $ ]);
                            return null === e ? null : [ e[0], e[2] ];
                        }

                        function E() {
                            var e = n([ x, i, _ ]);
                            return null === e ? null : [ e[0], e[2] ];
                        }

                        function N() {
                            var e = n([ x, i ]);
                            return null === e ? null : [ e[0], "" ];
                        }

                        var M = t([ function () {
                                var e = n([ t([ T, E, N ]), r(0, j) ]);
                                return null === e ? null : e[0].concat(e[1]);
                            }, function () {
                                var e = n([ x, r(0, j) ]);
                                return null === e ? null : [ e[0] ].concat(e[1]);
                            } ]),
                            O = a("{{"),
                            q = a("}}"),
                            I = a("[["),
                            L = a("]]"),
                            F = a("["),
                            G = a("]");

                        function P() {
                            var e = n([ O, M, q ]);
                            return null === e ? null : e[1];
                        }

                        var D = t([ function () {
                            var e = n([ r(1, _), l, r(1, X) ]);
                            return null === e ? null : [ [ "CONCAT" ].concat(e[0]), [ "CONCAT" ].concat(e[2]) ];
                        }, function () {
                            var e = n([ r(1, _) ]);
                            return null === e ? null : [ [ "CONCAT" ].concat(e[0]) ];
                        } ]);

                        function S() {
                            var e = null;
                            var u = n([ I, D, L ]);
                            if (null !== u) {
                                var _d19 = u[1];
                                e = [ "WIKILINK" ].concat(_d19);
                            }
                            return e;
                        }

                        function H() {
                            var e = null;
                            var u = n([ F, r(1, W), s, r(1, X), G ]);
                            return null !== u && (e = [ "EXTLINK", 1 === u[1].length ? u[1][0] : [ "CONCAT" ].concat(u[1]), [ "CONCAT" ].concat(u[3]) ]), e;
                        }

                        var R = c(/^[A-Za-z]+/);

                        function B() {
                            var e = c(/^[^"]*/),
                                u = n([ m, e, m ]);
                            return null === u ? null : u[1];
                        }

                        function Z() {
                            var e = c(/^[^']*/),
                                u = n([ k, e, k ]);
                            return null === u ? null : u[1];
                        }

                        function K() {
                            var e = c(/^\s*=\s*/),
                                u = n([ s, R, e, t([ B, Z ]) ]);
                            return null === u ? null : [ u[1], u[3] ];
                        }

                        function U() {
                            var e = r(0, K)();
                            return Array.prototype.concat.apply([ "HTMLATTRIBUTES" ], e);
                        }

                        var W = t([ P, $, S, H, function () {
                                var e = r(1, w)();
                                return null === e ? null : e.join("");
                            } ]),
                            X = t([ P, $, S, H, function () {
                                var u = null;
                                var t = d,
                                    s = a("<"),
                                    l = c(/^\/?/),
                                    i = c(/^\s*>/),
                                    o = n([ s, R, U, l, i ]);
                                if (null === o) return null;
                                var f = d,
                                    h = o[1],
                                    b = r(0, X)(),
                                    m = d,
                                    k = n([ a("</"), R, i ]);
                                if (null === k) return [ "CONCAT", e.slice(t, f) ].concat(b);
                                var p = d,
                                    g = k[1],
                                    w = o[2];
                                if (function (e, u, d) {
                                    var t = arguments.length > 3 && arguments[3] !== undefined ? arguments[3] : {
                                        allowedHtmlElements: [ "b", "bdi", "del", "i", "ins", "u", "font", "big", "small", "sub", "sup", "h1", "h2", "h3", "h4", "h5", "h6", "cite", "code", "em", "s", "strike", "strong", "tt", "var", "div", "center", "blockquote", "ol", "ul", "dl", "table", "caption", "pre", "ruby", "rb", "rp", "rt", "rtc", "p", "span", "abbr", "dfn", "kbd", "samp", "data", "time", "mark", "li", "dt", "dd" ],
                                        allowedHtmlCommonAttributes: [ "id", "class", "style", "lang", "dir", "title", "aria-describedby", "aria-flowto", "aria-hidden", "aria-label", "aria-labelledby", "aria-owns", "role", "about", "property", "resource", "datatype", "typeof", "itemid", "itemprop", "itemref", "itemscope", "itemtype" ],
                                        allowedHtmlAttributesByElement: {}
                                    };
                                    if ((e = e.toLowerCase()) !== (u = u.toLowerCase()) || -1 === t.allowedHtmlElements.indexOf(e)) return !1;
                                    var n = /[\000-\010\013\016-\037\177]|expression|filter\s*:|accelerator\s*:|-o-link\s*:|-o-link-source\s*:|-o-replace\s*:|url\s*\(|image\s*\(|image-set\s*\(/i;
                                    for (var _u3 = 0, _r2 = d.length; _u3 < _r2; _u3 += 2) {
                                        var _r3 = d[_u3];
                                        if (-1 === t.allowedHtmlCommonAttributes.indexOf(_r3) && -1 === (t.allowedHtmlAttributesByElement[e] || []).indexOf(_r3) || "style" === _r3 && -1 !== d[_u3 + 1].search(n)) return !1;
                                    }
                                    return !0;
                                }(h, g, w.slice(1))) u = [ "HTMLELEMENT", h, w ].concat(b); else {
                                    var _d20 = function _d20(e) {
                                        return e.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
                                    };
                                    u = [ "CONCAT", _d20(e.slice(t, f)) ].concat(b, _d20(e.slice(m, p)));
                                }
                                return u;
                            }, function () {
                                var e = r(1, z)();
                                return null === e ? null : e.join("");
                            } ]),
                            _ = t([ P, $, function () {
                                var e = r(1, y)();
                                return null === e ? null : e.join("");
                            } ]),
                            J = function () {
                                var e = r(0, X)();
                                return null === e ? null : [ "CONCAT" ].concat(e);
                            }();
                        if (null === J || d !== e.length) throw new Error("Parse error at position " + d.toString() + " in input: " + e);
                        return J;
                    }(e, { wikilinks: this.wikilinks });
                    return this.emitter.emit(_d17, u);
                }
                return this.simpleParse(e, u);
            }
        }, {
            key: "simpleParse",
            value: function simpleParse(e, u) {
                return e.replace(/\$(\d+)/g, function (e, d) {
                    var t = parseInt(d, 10) - 1;
                    return void 0 !== u[t] ? u[t] : "$" + d;
                });
            }
        } ]);

        return a;
    }();

    var c = function () {
        function c(e) {
            _classCallCheck(this, c);

            this.sourceMap = new Map();
        }

        _createClass(c, [ {
            key: "load",
            value: function load(e, u) {
                if ("object" != (typeof e === "undefined" ? "undefined" : _typeof(e))) throw new Error("Invalid message source. Must be an object");
                if (u) {
                    if (!/^[a-zA-Z0-9-]+$/.test(u)) throw new Error("Invalid locale " + u);
                    for (var _d21 in e) {
                        if (0 !== _d21.indexOf("@")) {
                            if ("object" == _typeof(e[_d21])) return this.load(e);
                            if ("string" != typeof e[_d21]) throw new Error("Invalid message for message " + _d21 + " in " + u + " locale.");
                            break;
                        }
                    }
                    this.sourceMap.has(u) ? this.sourceMap.set(u, Object.assign(this.sourceMap.get(u), e)) : this.sourceMap.set(u, e);
                } else for (u in e) {
                    this.load(e[u], u);
                }
            }
        }, {
            key: "getMessage",
            value: function getMessage(e, u) {
                var d = this.sourceMap.get(u);
                return d ? d[e] : null;
            }
        }, {
            key: "hasLocale",
            value: function hasLocale(e) {
                return this.sourceMap.has(e);
            }
        } ]);

        return c;
    }();

    function Banana(e) {
        var _ref3 = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {},
            _ref3$finalFallback = _ref3.finalFallback,
            u = _ref3$finalFallback === undefined ? "en" : _ref3$finalFallback,
            d = _ref3.messages,
            _ref3$wikilinks = _ref3.wikilinks,
            t = _ref3$wikilinks === undefined ? !1 : _ref3$wikilinks;

        _classCallCheck(this, Banana);

        this.locale = e, this.parser = new a(this.locale, { wikilinks: t }), this.messageStore = new c(), d && this.load(d, this.locale), this.finalFallback = u, this.wikilinks = t;
    }

    _createClass(Banana, [ {
        key: "load",
        value: function load(e, u) {
            return this.messageStore.load(e, u || this.locale);
        }
    }, {
        key: "i18n",
        value: function i18n(e) {
            for (var _len = arguments.length, u = Array(_len > 1 ? _len - 1 : 0), _key = 1; _key < _len; _key++) {
                u[_key - 1] = arguments[_key];
            }

            return this.parser.parse(this.getMessage(e), u);
        }
    }, {
        key: "setLocale",
        value: function setLocale(e) {
            this.locale = e, this.parser = new a(this.locale, { wikilinks: this.wikilinks });
        }
    }, {
        key: "getFallbackLocales",
        value: function getFallbackLocales() {
            return [].concat(_toConsumableArray(u[this.locale] || []), [ this.finalFallback ]);
        }
    }, {
        key: "getMessage",
        value: function getMessage(e) {
            var u = this.locale,
                d = 0;
            var t = this.getFallbackLocales(this.locale);
            for (; u;) {
                var _n3 = u.split("-");
                var _r4 = _n3.length;
                do {
                    var _u4 = _n3.slice(0, _r4).join("-"),
                        _d22 = this.messageStore.getMessage(e, _u4);
                    if ("string" == typeof _d22) return _d22;
                    _r4--;
                } while (_r4);
                u = t[d], d++;
            }
            return e;
        }
    }, {
        key: "registerParserPlugin",
        value: function registerParserPlugin(e, u) {
            r.prototype[e] = u;
        }
    } ]);

    /**
     * @type {typeof Internal.Banana}
     */
    module.exports = Banana;

})();