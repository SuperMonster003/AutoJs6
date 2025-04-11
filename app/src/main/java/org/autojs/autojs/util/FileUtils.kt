package org.autojs.autojs.util

import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.util.FileUtils.TYPE.Companion.TYPE_NAME_PREFIX_REGEX
import java.io.File
import java.util.function.Predicate
import kotlin.text.RegexOption.IGNORE_CASE

/**
 * Created by Stardust on Mar 31, 2017.
 * Modified by SuperMonster003 as of Jan 19, 2023.
 * Transformed by SuperMonster003 on Jan 21, 2023.
 */
@Suppress("SpellCheckingInspection")
object FileUtils {

    @JvmStatic
    fun getExtension(file: File): String = getExtension(file.name)

    // @Hint by SuperMonster003 on Nov 22, 2024.
    //  ! For the filename ".nomedia", technically it is a Unix hidden file,
    //  ! the dot before the filename indicates it as a hidden file.
    //  ! Such files usually do not have an extension, ".nomedia" is its full filename.
    //  ! According to conventional file extension parsing logic,
    //  ! this filename will not be recognized as having an extension.
    //  ! zh-CN:
    //  ! 对于文件名 ".nomedia", 技术上讲它是一个 Unix 隐藏文件, 文件名前的点号表示其为隐藏文件.
    //  ! 这类文件通常没有扩展名, ".nomedia" 就是它的文件名.
    //  ! 根据常规的文件扩展名解析逻辑, 这个文件名不会被识别为具有扩展名的文件.
    @JvmStatic
    fun getExtension(fileName: String): String = when {
        fileName.isEmpty() -> ""
        else -> {
            val i = fileName.lastIndexOf('.')
            when {
                i <= 0 || i + 1 >= fileName.length -> ""
                else -> fileName.substring(i + 1)
            }
        }
    }

    private fun withRegexPrefix(regexGetter: () -> String) = TYPE_NAME_PREFIX_REGEX + regexGetter.invoke()

    // @Hint by SuperMonster003 on Dec 1, 2024.
    //  ! Inherited from the traditional style of Auto.js 4.1.1 Alpha2,
    //  ! Unicode characters are still used here to represent an icon (instead of using Drawable).
    //  ! This method increases difficulty, as it requires customization adjustments
    //  ! for each character to ensure display consistency as much as possible.
    //  ! Even with constraints on position and size through parameters, positional deviation may still occur
    //  ! due to different device screen resolutions (such as not being perfectly centered).
    //  ! This method also adds uncertainty, as the display of Unicode characters cannot be guaranteed
    //  ! to be consistent across different devices, and they may not display at all.
    //  ! Additionally, different fonts, operating systems, and display settings can all affect the final rendering effect.
    //  ! Finally, it has been tested that Unicode code points beyond U+FFFF almost entirely fail
    //  ! to display correctly (currently, the only one used is "U+1D160", i.e., the musical note character "𝅘𝅥𝅮").
    //  ! This means the range of available Unicode characters is reduced, making it very difficult
    //  ! to find characters that fit the semantics (for example, it's hard to find a character suitable for representing an APK file).
    //  !
    //  ! zh-CN:
    //  !
    //  ! 继承于 Auto.js 4.1.1 Alpha2 的传统风格, 这里依然使用 Unicode 字符来表示一个图标 (而非使用 Drawable).
    //  ! 这样的方式增加了难度, 因为需要对每一个字符进行定制化调整, 以尽可能保证显示一致性.
    //  ! 即便通过参数对位置和大小进行了约束, 依然可能因设备屏幕分辨率不同而出现位置偏差 (如无法完全居中).
    //  ! 这样的方式同时也增加了不确定性, 因为 Unicode 字符在不同设备的显示方式无法保证一致, 甚至可能无法显示.
    //  ! 同时, 不同字体, 不同操作系统, 不同显示设置等, 都可能影响最终的渲染效果.
    //  ! 最后, 经测试, Unicode 码点超过 U+FFFF 时, 几乎全部无法正常显示 (目前唯一使用的是 "U+1D160", 即音符字符 "𝅘𝅥𝅮").
    //  ! 这意味着 Unicode 可选择的范围被缩小, 要找到符合语义的字符会十分困难 (例如很难找到一个字符适合用来表示 APK 文件).
    /**
     * A holder for various types of data representations with associated icons and identities.
     *
     * zh-CN: 一个用于存储各种类型数据表示的容器, 并附有相关的图标信息和类型标识.
     */
    internal object TypeDataHolder {
        val JAVASCRIPT = TypeData(IconData("J"), TYPE.IDENTITY_EXECUTABLE or TYPE.IDENTITY_TEXT_EDITABLE)
        val AUTO = TypeData(IconData("R", toTop = 1), TYPE.IDENTITY_EXECUTABLE or TYPE.IDENTITY_TEXT_EDITABLE)
        val APK = TypeData(IconData("❖", size = 23, toTop = 1, toEnd = 0.5), TYPE.IDENTITY_INSTALLABLE)
        val APK_EXT = TypeData(APK.iconData)
        val PROJECT = TypeData(IconData("✲", size = 22, toTop = 1.5), TYPE.IDENTITY_TEXT_EDITABLE)
        val TEXT = TypeData(IconData("✐", toTop = 1, toEnd = 0.5, degree = 121), TYPE.IDENTITY_TEXT_EDITABLE)
        val CREATION = TypeData(IconData("✎", toTop = 4, toEnd = 2, degree = 105), TYPE.IDENTITY_TEXT_EDITABLE)
        val CONFIG = TypeData(IconData("⚙", toStart = 0.5, toTop = 1), TYPE.IDENTITY_TEXT_EDITABLE)
        val DOCUMENT = TypeData(IconData("⌸", size = 24, toTop = 0.5, toEnd = 0.5), TYPE.IDENTITY_EXTERNAL_EDITABLE)
        val DISK_IMAGE = TypeData(IconData("⊚", size = 25, toTop = 1))
        val DISK_IMAGE_CUE_SHEET = TypeData(DISK_IMAGE.iconData, TYPE.IDENTITY_TEXT_EDITABLE)
        val CODE = TypeData(IconData("⌗", toTop = 1), TYPE.IDENTITY_TEXT_EDITABLE)
        val COMPILE = TypeData(IconData("☍"))
        val ARCHIVE = TypeData(IconData("❒", toTop = 3, toEnd = 0.5))
        val FIRMWARE = TypeData(IconData("⩩", size = 22, toStart = 0.5, toTop = 1))
        val EXECUTABLE = TypeData(IconData("⧉", size = 17, toStart = 0.5, toTop = 2))
        val CERTIFICATE = TypeData(IconData("⩮", size = 27))
        val LICENSE = TypeData(IconData("≚", size = 27), TYPE.IDENTITY_TEXT_EDITABLE)
        val SIGNATURE = TypeData(IconData("⩛", size = 27, toBottom = 1))
        val DATA = TypeData(IconData("⛃", toTop = 0.5))
        val DATA_RELATED = TypeData(IconData("⛁", toTop = 0.5))
        val PICTURE = TypeData(IconData("☘", toTop = 0.5, toEnd = 0.5), TYPE.IDENTITY_EXTERNAL_EDITABLE)
        val DRAWING = TypeData(IconData("⦬", size = 25, toTop = 2, excludeFontPadding = true))
        val AUDIO = TypeData(IconData("𝅘𝅥𝅮", size = 20, toEnd = 1, toBottom = 5), TYPE.IDENTITY_MEDIA_PLAYABLE)
        val AUDIO_PLAYLIST = TypeData(AUDIO.iconData, TYPE.IDENTITY_MEDIA_PLAYABLE or TYPE.IDENTITY_TEXT_EDITABLE)
        val AUDIO_CUE_SHEET = TypeData(AUDIO.iconData, TYPE.IDENTITY_MEDIA_PLAYABLE or TYPE.IDENTITY_TEXT_EDITABLE)
        val VIDEO = TypeData(IconData("ᐅ", size = 23, toTop = 0.5, toEnd = 3.5), TYPE.IDENTITY_MEDIA_PLAYABLE)
        val VIDEO_PLAYLIST = TypeData(VIDEO.iconData, TYPE.IDENTITY_MEDIA_PLAYABLE or TYPE.IDENTITY_TEXT_EDITABLE)
        val SUBTITLE = TypeData(IconData("⩸", size = 21, toTop = 1), TYPE.IDENTITY_TEXT_EDITABLE)
        val MEDIA_MENU = TypeData(IconData("ꘈ", size = 19, toStart = 0.5, toTop = 0.5), TYPE.IDENTITY_MEDIA_MENU)
        val ENCRYPTED_MEDIA = TypeData(IconData("☊", size = 23, toEnd = 0.5))
        val GAME = TypeData(IconData("ⵘ", size = 26, toTop = 1))
        val FONT = TypeData(IconData("ꭲ", size = 23, toTop = 3.5, excludeFontPadding = true))
        val LINK = TypeData(IconData("ꮣ", size = 24, toTop = 3.5, excludeFontPadding = true))
        val OTHERS = TypeData(IconData("⚬", size = 25, toTop = 1))
    }

    /**
     * Enum class for file types.
     *
     * zh-CN: 文件类型枚举类.
     *
     * @param typeName The type name, used for type identification and extension generation.
     *
     * zh-CN: 类型名称, 用于类型识别, 也用于扩展名生成.
     *
     * @param typeData The type data, including icon data (Unicode characters, padding data, font size, etc.)
     * and type identification (e.g., playable, editable, executable).
     *
     * zh-CN: 类型数据, 包含图标数据 (Unicode 字符, 填充数据, 字体大小等) 和类型标识 (如可播放, 可编辑, 可执行等).
     *
     * @param candidateCriteria The candidate matching criteria. When the type names are the same,
     * further type differentiation is made based on the established criteria.
     *
     * zh-CN: 候选匹配标准. 当类型名称相同时, 根据既定标准做进一步的类型判别.
     *
     * @param priority The type priority. When the type names are the same
     * and the weight scores of all candidate matching criteria are also the same,
     * final type differentiation is made based on priority.
     *
     * zh-CN: 类型优先级. 当类型名称相同, 且所有候选匹配标准的权重评分也相同时, 根据优先级做最终的类型判别.
     */
    enum class TYPE(
        val typeName: String,
        val typeData: TypeData = TypeData(iconData = IconData()),
        vararg val candidateCriteria: CandidateCriterion,
        val priority: Int = 0,
    ) {

        // 目录 (Directory)

        DIRECTORY(File.separator),

        // 可执行项目 (Executable Project)

        PROJECT(ProjectConfig.CONFIG_FILE_NAME, TypeDataHolder.PROJECT),

        // 可执行脚本 (Executable script)

        JAVASCRIPT("js", TypeDataHolder.JAVASCRIPT),
        AUTO("auto", TypeDataHolder.AUTO),

        // 编程语言 (Programming language)

        /** Java source file, used in Java applications. */
        JAVA("java", TypeDataHolder.CODE),

        /** TypeScript source file, a typed superset of JavaScript. */
        TYPESCRIPT(
            "ts", TypeDataHolder.CODE,
            CandidateCriterion({ it.isTypeScriptLike() }, 10),
            CandidateCriterion({ it.isSizeLessThan(100, SizeUnit.KB) }, 5),
            CandidateCriterion({ it.isSizeLessThan(1, SizeUnit.MB) }, 1),
            priority = 1,
        ),

        /** TypeScript declaration file, describes the types of a module. */
        TYPESCRIPT_DECLARATION("d.ts", TypeDataHolder.CREATION),

        /** React TypeScript file, used for React components with TypeScript. */
        REACT_TYPESCRIPT("tsx", TypeDataHolder.CODE),

        /** React JavaScript file, used for React components with JavaScript. */
        REACT_JAVASCRIPT("jsx", TypeDataHolder.CODE),

        /** ECMAScript Module, JavaScript module file. */
        MODULE_JAVASCRIPT("mjs", TypeDataHolder.CODE),

        /** Embedded JavaScript templating file, used for HTML templates. */
        EJS("ejs", TypeDataHolder.CODE),

        /** NFEX code file. */
        NFEX("nfex", TypeDataHolder.CODE),

        /** Kotlin source file, used in Kotlin applications. */
        KOTLIN("kt", TypeDataHolder.CODE),

        /** Kotlin script file, used for scripting with Kotlin. */
        KOTLIN_SCRIPT("kts", TypeDataHolder.CODE),

        /** C++ source file, used in C++ applications. */
        CPP("cpp", TypeDataHolder.CODE),

        /** Alternative C++ source file extension. */
        CXX("cxx", TypeDataHolder.CODE),

        /** C header file, used for C and C++ declarations. */
        H("h", TypeDataHolder.CODE),

        /** C source file, used in C applications. */
        C("c", TypeDataHolder.CODE),

        /** Additional C++ source file extension. */
        CC("cc", TypeDataHolder.CODE),

        /** C++ header file. */
        HPP("hpp", TypeDataHolder.CODE),

        /** C# source file, used for applications in C#. */
        C_SHARP("cs", TypeDataHolder.CODE),

        /** Include file, commonly used in various languages for shared code. */
        INC("inc", TypeDataHolder.CODE),

        /** Python source file, used in Python applications. */
        PYTHON("py", TypeDataHolder.CODE),

        /** Ruby source file, used for Ruby programming. */
        RUBY("rb", TypeDataHolder.CODE),

        /** Go source file, used in Go applications. */
        GO("go", TypeDataHolder.CODE),

        /** Rust source file, used in Rust programming. */
        RUST("rs", TypeDataHolder.CODE),

        /** Lua script file, used for Lua scripting language. */
        LUA("lua", TypeDataHolder.CODE),

        /** PHP script file, used in server-side scripting. */
        PHP("php", TypeDataHolder.CODE),

        /** Volt template file, used for web templating with Volt. */
        VOLT("volt", TypeDataHolder.CODE),

        /** PHP HTML file, used in PHP applications. */
        PHTML("pthml", TypeDataHolder.CODE),

        /** Swift source file, used for Swift programming language. */
        SWIFT("swift", TypeDataHolder.CODE),

        /** Objective-C source file, used in Cocoa and iOS development. */
        OBJECT_C(
            "m", TypeDataHolder.CODE,
            CandidateCriterion({ it.isObjectiveCLike() }, 10),
            priority = 1,
        ),

        /** Objective-C++ source file, combines C++ and Objective-C. */
        OBJECT_CPP("mm", TypeDataHolder.CODE),

        /** MATLAB script file, used for numerical computing with MATLAB. */
        MATLAB(
            "m", TypeDataHolder.CODE,
            CandidateCriterion({ it.isMatlabLike() }, 10),
        ),

        /** Perl script file, used in Perl scripting. */
        PERL("pl", TypeDataHolder.CODE),

        /** Perl module file, used for Perl package components. */
        PERL_MODULE("pm", TypeDataHolder.CODE),

        /** Perl POD file, documentation format for Perl. */
        PERL_POD("pod", TypeDataHolder.CODE),

        /** R script file, used for statistical computing in R. */
        R("r", TypeDataHolder.CODE),

        /** Dart source file, used for Dart programming language. */
        DART("dart", TypeDataHolder.CODE),

        /** F# source file, used in F# programming language. */
        F_SHARP("fs", TypeDataHolder.CODE),

        /** F# script file, used for scripting with F#. */
        F_SHARP_EXT("fsx", TypeDataHolder.CODE),

        /** Racket source code file, used in the Racket programming language. */
        RACKET("rkt", TypeDataHolder.CODE),

        /** Julia source code file, associated with the Julia programming language. */
        JULIA("jl", TypeDataHolder.CODE),

        /** Haskell source code file, associated with the Haskell programming language. */
        HASKELL("hs", TypeDataHolder.CODE),

        /** Erlang source code file, used in the Erlang programming language. */
        ERLANG("erl", TypeDataHolder.CODE),

        /** Elixir script file, used in the Elixir programming language. */
        ELIXIR("exs", TypeDataHolder.CODE),

        /** FORTRAN 90 source code file, used for programming in Fortran 90. */
        FORTRAN_90("f90", TypeDataHolder.CODE),

        /** FORTRAN 95 source code file, used for programming in Fortran 95. */
        FORTRAN_95("f95", TypeDataHolder.CODE),

        /** COBOL source code file, associated with the COBOL programming language. */
        COBOL("col", TypeDataHolder.CODE),

        /** Tcl script file, used in Tcl programming language. */
        TCL("tcl", TypeDataHolder.CODE),

        /** IDL file, Interface Definition Language for CORBA. */
        IDL("idl", TypeDataHolder.CODE),

        /** COBOL source file, used in COBOL programming language. */
        COB("cob", TypeDataHolder.CODE),

        /** REBOL script file, used in REBOL programming. */
        REBOL("reb", TypeDataHolder.CODE),

        /** ECMAScript module file. */
        ESM("esm", TypeDataHolder.CODE),

        /** Fortran source file, used in Fortran programming. */
        FORTRAN("for", TypeDataHolder.CODE),

        /** Macro file, used for various macro languages. */
        MACRO(
            "mac", TypeDataHolder.CODE,
            CandidateCriterion({ it.isMacroFileLike() }, 10),
            priority = 5,
        ),

        /** Embedded Visual Basic file. */
        EVB("evb", TypeDataHolder.CODE),

        /** Lisp source file, used in Lisp programming languages. */
        NEW_LISP("lsp", TypeDataHolder.CODE),

        /** NodeBrain rule file, used in NodeBrain programming. */
        NODE_BRAIN("nb", TypeDataHolder.CODE),

        /** NSIS script file, used for Nullsoft Scriptable Install System. */
        NSIS("nsi", TypeDataHolder.CODE),

        /** Pascal source file, used in Pascal programming language. */
        PASCAL("pas", TypeDataHolder.CODE),

        /** Resource script file, used for Windows resources. */
        RC("rc", TypeDataHolder.CODE),

        /** Apache Velocity template file, used for web templating. */
        VELOCITY("vm", TypeDataHolder.CODE),

        /** Verilog source file, used in hardware description. */
        VERILOG("v", TypeDataHolder.CODE),

        /** Verilog header file. */
        VERILOG_HEADER("vh", TypeDataHolder.CODE),

        /** Verilog test bench file, used for testing designs. */
        VERILOG_TEST_BENCH("vt", TypeDataHolder.CODE),

        /** Hewlett-Packard Graphics Language file, used for plotter instructions. */
        HPGL("hpgl", TypeDataHolder.CODE),

        /** System Verilog source file, advanced hardware description. */
        SYSTEM_VERILOG("sv", TypeDataHolder.CODE),

        /** System Verilog header file. */
        SYSTEM_VERILOG_HEADER("svh", TypeDataHolder.CODE),

        // 脚本 (Script)

        /** AviSynth script file, used for video editing with AviSynth. */
        AVS("avs", TypeDataHolder.CODE),

        /** Shell script file, used in Unix-like systems for command execution. */
        SHELL("sh", TypeDataHolder.CODE),

        /** Batch script file for Windows, used for executing commands. */
        BATCH("bat", TypeDataHolder.CODE),

        /** Command script file, another format for batch files on Windows. */
        COMMAND("cmd", TypeDataHolder.CODE),

        /** PowerShell script file, used for task automation on Windows. */
        POWERSHELL("ps1", TypeDataHolder.CODE),

        /** PowerShell data file, used for configuration data in PowerShell scripts. */
        POWERSHELL_DATA("psd1", TypeDataHolder.CODE),

        /** PowerShell script module, encapsulates reusable PowerShell functions. */
        POWERSHELL_SCRIPT_MODULE("psm1", TypeDataHolder.CODE),

        /** Visual Basic script file, used for scripting in VB environments. */
        VB("vb", TypeDataHolder.CODE),

        /** VBScript file, used for scripting within Windows and enhancing webpages. */
        VB_SCRIPT("vbs", TypeDataHolder.CODE),

        /** Visual Basic Form file, part of Visual Basic projects. */
        FRM("frm", TypeDataHolder.CODE),

        /** Visual Basic module file, used in VB applications. */
        BAS("bas", TypeDataHolder.CODE),

        /** Groovy script file, used for scripting in the Groovy language. */
        GROOVY("groovy", TypeDataHolder.CODE),

        /** Gradle build script file, used for building projects with Gradle. */
        GRADLE("gradle", TypeDataHolder.CODE),

        /** Gradle wrapper script, used to execute Gradle tasks. */
        GRADLE_WRAPPER("gradlew", TypeDataHolder.CODE),

        /** Kotlin-based Gradle build script. */
        GRADLE_KOTLIN("gradle.kts", TypeDataHolder.CODE),

        /** AppleScript file, used for automating tasks on macOS. */
        APPLE_SCRIPT("applescript", TypeDataHolder.CODE),

        /** AutoHotkey script file, used for automation on Windows. */
        AHK("ahk", TypeDataHolder.CODE),

        /** Assembly language source file, used for low-level programming. */
        ASM("asm", TypeDataHolder.CODE),

        /** AWK script file, used for text processing. */
        AWK("awk", TypeDataHolder.CODE),

        /** Batch to memory script, used for older DOS batch processing. */
        BTM("btm", TypeDataHolder.CODE),

        /** CoffeeScript file, a JavaScript variant with a more concise syntax. */
        COFFEE("coffee", TypeDataHolder.CODE),

        /** Embedded Ruby template, used for embedding Ruby in HTML. */
        EMBEDDED_RUBY("erb", TypeDataHolder.CODE),

        /** mIRC script file, used for automating IRC client tasks. */
        MRC("mrc", TypeDataHolder.CODE),

        /** ASP.NET Web Form, used for server-side web applications. */
        ASPX("aspx", TypeDataHolder.CODE),

        /** Windows Script File, used for scripting Windows tasks leveraging COM objects. */
        WSF("wsf", TypeDataHolder.CODE),

        /** AutoIt v3 script, used for automating the Windows GUI. */
        AU3("au3", TypeDataHolder.CODE),

        /** Vim script file, used for scripting and configuring the Vim editor. */
        VIM("vim", TypeDataHolder.CODE),

        /** PostScript file for representing graphics in printing. */
        PS("ps", TypeDataHolder.CODE),

        /** Encapsulated PostScript file, used for complex graphics. */
        EPS("eps", TypeDataHolder.CODE),

        /** Updater script file, used typically for custom ROM installations. */
        UPDATER_SCRIPT("updater-script", TypeDataHolder.CODE),

        // 文本 (Text)

        /** *.log.1, *.log.2, *.log.3, ... */
        LOG_WITH_NUM(withRegexPrefix { "log\\.\\d+" }, TypeDataHolder.TEXT),

        /** Plain text file, used for unformatted text. */
        TEXT("txt", TypeDataHolder.TEXT),

        /** Diff file, contains differences between two files or sets of files. */
        DIFF("diff", TypeDataHolder.TEXT),

        /** Patch file, used to apply changes to a source code file. */
        PATCH("patch", TypeDataHolder.TEXT),

        /** Readme file, typically contains information about a software project. */
        README("readme", TypeDataHolder.TEXT),

        /** Log file, commonly records events and messages logged by software. */
        LOG("log", TypeDataHolder.TEXT),

        /** Info file, often contains system and resource information. */
        NFO("nfo", TypeDataHolder.TEXT),

        /** Rich Text Format, supports text formatting. */
        RTF("rtf", TypeDataHolder.TEXT),

        /** Hexadecimal representation file, used for binary data viewing. */
        HEX("hex", TypeDataHolder.TEXT),

        /** WinBatch Text file, used in WinBatch scripting. */
        WTX("wtx", TypeDataHolder.TEXT),

        /** Script file, a generic extension used in various contexts. */
        SCP("scp", TypeDataHolder.TEXT),

        /** Windows Error Report file, generated when a program crashes. */
        WER("wer", TypeDataHolder.TEXT),

        // 许可 (License)

        /** License file, contains the terms under which software can be used. */
        LIC("lic", TypeDataHolder.LICENSE),

        /** License file, often used to specify software usage rights. */
        LICENSE("license", TypeDataHolder.LICENSE),

        /** Copyright file, states ownership and rights over the content. */
        COPYRIGHT("copyright", TypeDataHolder.LICENSE),

        // 标记语言 (Markup language)

        /** JSON file, used for data interchange format in human-readable text. */
        JSON("json", TypeDataHolder.CREATION),

        /** XML file, a markup language for encoding documents. */
        XML("xml", TypeDataHolder.CREATION),

        /** WXML file, used in WeChat Mini Programs for layout and interface. */
        WXML("wxml", TypeDataHolder.CREATION),

        /** YAML file, data serialization standard for configuration files and data exchange. */
        YAML("yaml", TypeDataHolder.CREATION),

        /** YAML alternative file extension. */
        YML("yml", TypeDataHolder.CREATION),

        /** Scalable Vector Graphics file, used for vector images. */
        SVG("svg", TypeDataHolder.CREATION),

        /** HTML file, standard markup language for web pages. */
        HTML("html", TypeDataHolder.CREATION),

        /** Alternative HTML file extension. */
        HTM("htm", TypeDataHolder.CREATION),

        /** Server-side include HTML file, used for dynamic webpages. */
        SHTML("shtml", TypeDataHolder.CREATION),

        /** MIME Encapsulation of Aggregate HTML documents. */
        HMTML("mhtml", TypeDataHolder.CREATION),

        /** Multipurpose Internet Mail Extensions HTML file. */
        MHT("mht", TypeDataHolder.CREATION),

        /** HTML Application file, used to run applications full-trust locally. */
        HTA("hta", TypeDataHolder.CREATION),

        /** XHTML file, a more strict and XML-compatible HTML version. */
        XHT("xht", TypeDataHolder.CREATION),

        /** LaTeX file, used for high-quality typesetting. */
        LTX("ltx", TypeDataHolder.CREATION),

        /** Document Type Definition file, defines XML document structure. */
        DTD("dtd", TypeDataHolder.CREATION),

        /** Classpath file, used in Java projects to define the classpath for compilation. */
        CLASSPATH("classpath", TypeDataHolder.CREATION),

        /** Markdown file, used for simple document formatting. */
        MARKDOWN(
            "md", TypeDataHolder.CREATION,
            CandidateCriterion({ it.isMarkdownMDLike() }, 10),
            priority = 8,
        ),

        /** Browser configuration file. */
        BROWSER("browser", TypeDataHolder.CREATION),

        /** XAML Browser Application file, used for building Windows apps. */
        XBAP("xbap", TypeDataHolder.CREATION),

        /** Web Services Description Language file, used in web services. */
        WSDL("wsdl", TypeDataHolder.CREATION),

        /** Active Server Pages file, used in server-side scripting. */
        ASP("asp", TypeDataHolder.CREATION),

        /** JavaServer Pages file, used for dynamic web content in Java. */
        JSP("jsp", TypeDataHolder.CREATION),

        /** Information Presentation Facility file, used for documentation presentation. */
        IPF("ipf", TypeDataHolder.CREATION),

        /** Build targets file for project configuration. */
        TARGETS("targets", TypeDataHolder.CREATION),

        /** Project Object Model file, used in Apache Maven for dependency management. */
        POM("pom", TypeDataHolder.CREATION),

        /** Flash XML Graphics file, used for vector graphics in flash projects. */
        FXG("fxg", TypeDataHolder.CREATION),

        /** Composite font file, used in font mapping. */
        COMPOSITEFONT("compositefont", TypeDataHolder.CREATION),

        /** Microsoft Update Manifest file, used in Windows system updates. */
        MUM("mum", TypeDataHolder.CREATION),

        /** Windows Script Component file, used for creating reusable software components. */
        WSC("wsc", TypeDataHolder.CREATION),

        /** Proxy Auto-Configuration file, used to determine proxy for fetching URLs. */
        PRX("prx", TypeDataHolder.CREATION),

        /** Administrative Template XML file for Group Policy. */
        ADMX("admx", TypeDataHolder.CREATION),

        /** Administrative Template XML Language file, contains localization info. */
        ADML("adml", TypeDataHolder.CREATION),

        /** PowerShell XML format file, used to define views for PowerShell objects. */
        PS1XML("ps1xml", TypeDataHolder.CREATION),

        /** Command and syntax reference manual file. */
        MAN("man", TypeDataHolder.CREATION),

        /** .NET resource file, used to store resources for applications. */
        RESX("resx", TypeDataHolder.CREATION),

        /** Microsoft Text XML, used for packaging files in certain apps. */
        PTXML("ptxml", TypeDataHolder.CREATION),

        /** XrML - XML license for managing rights and policies. */
        XRM_MS("xrm-ms", TypeDataHolder.CREATION),

        /** Microsoft Help 2 index file, used in documentation systems. */
        HXA("hxa", TypeDataHolder.CREATION),

        /** Microsoft Help 2 contents file, used in documentation systems. */
        HXK("hxk", TypeDataHolder.CREATION),

        /** Microsoft Help 2 query file, used in documentation systems. */
        HXQ("hxq", TypeDataHolder.CREATION),

        /** Microsoft Help HTML layout file, used for online help documentation. */
        H1S("h1s", TypeDataHolder.CREATION),

        /** Microsoft Help 2 title file, used in documentation systems. */
        HXT("hxt", TypeDataHolder.CREATION),

        /** Microsoft Help 2 aggregated namespace file, used in documentation systems. */
        HXS("hxs", TypeDataHolder.CREATION),

        /** Microsoft Help 2 collection definition file, used in help systems. */
        HXC("hxc", TypeDataHolder.CREATION),

        /** Manifest file, often contains metadata about a group of files. */
        MANIFEST("manifest", TypeDataHolder.CREATION),

        /** Property list file, used for Apple's macOS & iOS app configuration. */
        PLIST("plist", TypeDataHolder.CREATION),

        /** XML Schema Definition, describes the structure of XML data. */
        XSD("xsd", TypeDataHolder.CREATION),

        // 样式表 (Stylesheet)

        /** Cascading Style Sheets file, used for styling HTML documents. */
        CSS("css", TypeDataHolder.CREATION),

        /** WeChat Mini Program styles file, used in WeChat app development. */
        WXSS("wxss", TypeDataHolder.CREATION),

        /** Sassy CSS file, an extension of CSS enabling variables and nested rules. */
        SCSS("scss", TypeDataHolder.CREATION),

        /** Syntactically Awesome Style Sheets file, another form of SASS without brackets. */
        SASS("sass", TypeDataHolder.CREATION),

        /** Less file, a pre-processor for CSS allowing variables and mixins. */
        LESS("less", TypeDataHolder.CREATION),

        /** TeX file, a typesetting system widely used for mathematical and scientific documents. */
        TEX("tex", TypeDataHolder.CREATION),

        /** XSL Stylesheet file, used for XML data transformations. */
        XSL("xsl", TypeDataHolder.CREATION),

        /** XSLT Stylesheet file, specifically for extended transformations. */
        XSLT("xslt", TypeDataHolder.CREATION),

        // 字体 (Font)

        /** TrueType Font, widely used fonts that can be scaled without quality loss. */
        TTF("ttf", TypeDataHolder.FONT),

        /** OpenType Font, an extension of TrueType with support for more advanced typographic features. */
        OTF("otf", TypeDataHolder.FONT),

        /** Font file, an older bitmap font format used in Windows. */
        FON("fon", TypeDataHolder.FONT),

        /** Multiple Master Metric file, used for fonts that define multiple design variations. */
        MMM("mmm", TypeDataHolder.FONT),

        /** Adobe Font Metrics, contains font metric information for a PostScript font. */
        AFM("afm", TypeDataHolder.FONT),

        /** Glyph Bitmap Distribution Format (BDF), a bitmap font format by Adobe. */
        BDF("bdf", TypeDataHolder.FONT),

        /** Printer Font Binary, a binary file containing a Type 1 font outline. */
        PFB("pfb", TypeDataHolder.FONT),

        /** Printer Font Metrics, contains metric information for a Type 1 font. */
        PFM("pfm", TypeDataHolder.FONT),

        // 文档 (Document)

        /** Microsoft Word document, primarily for text processing. */
        DOC("doc", TypeDataHolder.DOCUMENT),

        /** Microsoft Word Open XML document, an advanced Word format supporting more features. */
        DOCX("docx", TypeDataHolder.DOCUMENT),

        /** Auto-Save Document, used by Microsoft Word for recovery purposes. */
        ASD("asd", TypeDataHolder.DOCUMENT),

        /** Word document supporting macros. */
        DOCM("docm", TypeDataHolder.DOCUMENT),

        /** Word template document, used for creating template files in Word. */
        DOT("dot", TypeDataHolder.DOCUMENT),

        /** Word template supporting macros, for advanced templates. */
        DOTM("dotm", TypeDataHolder.DOCUMENT),

        /** XML Word template, offers features without advanced capabilities of macros. */
        DOTX("dotx", TypeDataHolder.DOCUMENT),

        /** OpenDocument Text file, open standard document format often used with OpenOffice. */
        ODT("odt", TypeDataHolder.DOCUMENT),

        /** Word Backup file, used for Word document backups. */
        WBK("wbk", TypeDataHolder.DOCUMENT),

        /** Wizard file used in Microsoft Word for creating Wizards. */
        WIZ("wiz", TypeDataHolder.DOCUMENT),

        /** Microsoft PowerPoint presentation file, used for creating slide shows. */
        PPT("ppt", TypeDataHolder.DOCUMENT),

        /** PowerPoint Open XML presentation, newer version with more functionality. */
        PPTX("pptx", TypeDataHolder.DOCUMENT),

        /** OpenDocument Presentation file, part of the OpenOffice formats. */
        ODP("odp", TypeDataHolder.DOCUMENT),

        /** PowerPoint template file, used for creating PowerPoint templates. */
        POT("pot", TypeDataHolder.DOCUMENT),

        /** Macro-enabled PowerPoint template file. */
        POTM("potm", TypeDataHolder.DOCUMENT),

        /** XML template for PowerPoint presentations without macros. */
        POTX("potx", TypeDataHolder.DOCUMENT),

        /** PowerPoint Add-in file, used to extend PowerPoint functionalities. */
        PPA("ppa", TypeDataHolder.DOCUMENT),

        /** Macro-enabled PowerPoint Add-in file. */
        PPAM("ppam", TypeDataHolder.DOCUMENT),

        /** PowerPoint slide show file, used for a full-screen slide-show. */
        PPS("pps", TypeDataHolder.DOCUMENT),

        /** PowerPoint macro-enabled slide show file. */
        PPSM("ppsm", TypeDataHolder.DOCUMENT),

        /** PowerPoint Open XML Slide Show file. */
        PPSX("ppsx", TypeDataHolder.DOCUMENT),

        /** PowerPoint macro-enabled presentation file. */
        PPTM("pptm", TypeDataHolder.DOCUMENT),

        /** PowerPoint Wizard file. */
        PWZ("pwz", TypeDataHolder.DOCUMENT),

        /** Microsoft Works Word Processor document. */
        WPS("wps", TypeDataHolder.DOCUMENT),

        /** XML Paper Specification, fixed-layout document format developed by Microsoft. */
        XPS("xps", TypeDataHolder.DOCUMENT),

        /** Open XML Paper Specification, a newer version with open XML. */
        OXPS("oxps", TypeDataHolder.DOCUMENT),

        /** Portable Document Format, widely used for documents that can be read across various systems. */
        PDF("pdf", TypeDataHolder.DOCUMENT),

        /** StarOffice Calc Spreadsheet, used before OpenOffice.org adoption. */
        SXC("sxc", TypeDataHolder.DOCUMENT),

        /** Visio Drawing file, used for vector graphic representations in business processes. */
        VSD("vsd", TypeDataHolder.DOCUMENT),

        /** Visio Stencil file, used to store shapes for use in Visio documents. */
        VSS("vss", TypeDataHolder.DOCUMENT),

        /** Visio XML Drawing file. */
        VDX("vdx", TypeDataHolder.DOCUMENT),

        /** Adobe Color Book file, used by Adobe software for managing color palettes. */
        ACB("acb", TypeDataHolder.DOCUMENT),

        /** Visio Template file, contains pre-defined designs to create Visio drawings. */
        VST("vst", TypeDataHolder.DOCUMENT),

        /** Visio XML Stencil file. */
        VSX("vsx", TypeDataHolder.DOCUMENT),

        /** Visio XML Template file. */
        VTX("vtx", TypeDataHolder.DOCUMENT),

        /** Adobe Illustrator file, used for creating and editing vector graphics. */
        AI("ai", TypeDataHolder.DOCUMENT),

        /** Adobe Illustrator Template, for defining Adobe Illustrator templates. */
        AIT("ait", TypeDataHolder.DOCUMENT),

        /** Adobe Illustrator Plug-in, used to extend functionalities of Adobe Illustrator. */
        AIP("aip", TypeDataHolder.DOCUMENT),

        /** OpenOffice Drawing file, used for storing graphic data. */
        OGD("ogd", TypeDataHolder.DOCUMENT),

        /** Universal Mobile Document, used for ebooks mainly in Chinese markets. */
        UMD("umd", TypeDataHolder.DOCUMENT),

        /** Windows Help file, used to provide help documentation for Windows software. */
        HLP("hlp", TypeDataHolder.DOCUMENT),

        /** Compiled HTML Help file, a format for Windows help files. */
        CHM("chm", TypeDataHolder.DOCUMENT),

        // 图像 (Image)

        /** Joint Photographic Experts Group. */
        JPG("jpg", TypeDataHolder.PICTURE),

        /** Joint Photographic Experts Group. */
        JPEG("jpeg", TypeDataHolder.PICTURE),

        /** Portable Network Graphics. */
        PNG("png", TypeDataHolder.PICTURE),

        /** Graphics Interchange Format. */
        GIF("gif", TypeDataHolder.PICTURE),

        /** Bitmap Image. */
        BMP("bmp", TypeDataHolder.PICTURE),

        /** Web Picture format. */
        WEBP("webp", TypeDataHolder.PICTURE),

        /** Tagged Image File Format. */
        TIFF("tiff", TypeDataHolder.PICTURE),

        /** Digital Negative format. */
        DNG("dng", TypeDataHolder.PICTURE),

        /** Adobe Photoshop Document. */
        PSD("psd", TypeDataHolder.PICTURE),

        /** Windows Metafile. */
        WMF("wmf", TypeDataHolder.PICTURE),

        /** High Efficiency Image Coding. */
        HEIC("heic", TypeDataHolder.PICTURE),

        /** High Efficiency Image File Format. */
        HEIF("heif", TypeDataHolder.PICTURE),

        /** Icon File. */
        ICO("ico", TypeDataHolder.PICTURE),

        /** JPEG File Interchange Format. */
        JFIF("jfif", TypeDataHolder.PICTURE),

        /** Enhanced Metafile Format. */
        EMF("emf", TypeDataHolder.PICTURE),

        /** High Efficiency Image Format. */
        HIF("hif", TypeDataHolder.PICTURE),

        /** JPEG Image File. */
        JPE("jpe", TypeDataHolder.PICTURE),

        /** Cursor File. */
        CUR("cur", TypeDataHolder.PICTURE),

        /** Device Independent Bitmap File. */
        DIB("dib", TypeDataHolder.PICTURE),

        /** Sony Alpha Raw. */
        ARW("arw", TypeDataHolder.PICTURE),

        /** Canon Raw 2. */
        CR2("cr2", TypeDataHolder.PICTURE),

        /** Canon Raw. */
        CRW("crw", TypeDataHolder.PICTURE),

        /** Minolta Raw. */
        MRW("mrw", TypeDataHolder.PICTURE),

        /** Nikon Raw Image */
        NRW("nrw", TypeDataHolder.PICTURE),

        /** Nikon Electronic Format. */
        NEF("nef", TypeDataHolder.PICTURE),

        /** Olympus Raw File. */
        ORF("orf", TypeDataHolder.PICTURE),

        /** Pentax Electronic Format. */
        PEF("pef", TypeDataHolder.PICTURE),

        /** Fuji Raw Image File. */
        RAF("raf", TypeDataHolder.PICTURE),

        /** Panasonic Raw 2. */
        RW2("rw2", TypeDataHolder.PICTURE),

        /** Sony Raw Format. */
        SRF("srf", TypeDataHolder.PICTURE),

        /** Picture Exchange (ZSoft Paintbrush). */
        PCX("pcx", TypeDataHolder.PICTURE),

        /** Truevision TGA (TARGA). */
        TGA("tga", TypeDataHolder.PICTURE),

        /** Tagged Image File Format. */
        TIF("tif", TypeDataHolder.PICTURE),

        /** Run Length Encoded Bitmap. */
        RLE("rle", TypeDataHolder.PICTURE),

        /** Generic Picture Format. */
        PIC("pic", TypeDataHolder.PICTURE),

        /** Portable Pixmap Format. */
        PPM("ppm", TypeDataHolder.PICTURE),

        /** Portable Graymap Format. */
        PGM("pgm", TypeDataHolder.PICTURE),

        /** Portable Bitmap Format. */
        PBM("pbm", TypeDataHolder.PICTURE),

        /** JPEG XL Image. */
        JXL("jxl", TypeDataHolder.PICTURE),

        /** JPEG 2000 Code Stream. */
        JPC("jpc", TypeDataHolder.PICTURE),

        /** JPEG 2000 Image. */
        JP2("jp2", TypeDataHolder.PICTURE),

        /** JPEG 2000 File Format. */
        JPF("jpf", TypeDataHolder.PICTURE),

        /** JPEG 2000 Extended. */
        JPX("jpx", TypeDataHolder.PICTURE),

        /** JPEG 2000. */
        J2K("j2k", TypeDataHolder.PICTURE),

        /** Sun Raster Image. */
        RAS("ras", TypeDataHolder.PICTURE),

        /** Photo CD Image. */
        PCD("pcd", TypeDataHolder.PICTURE),

        /** X Bitmap Image. */
        XBM("xbm", TypeDataHolder.PICTURE),

        /** X PixMap Image. */
        XPM("xpm", TypeDataHolder.PICTURE),

        /** GIF Video. */
        GIFV("gifv", TypeDataHolder.PICTURE),

        /** DirectDraw Surface. */
        DDS("dds", TypeDataHolder.PICTURE),

        /** AV1 Image Format. */
        AVIF("avif", TypeDataHolder.PICTURE),

        /** Multi-bitmap, commonly for Symbian OS. */
        MBM("mbm", TypeDataHolder.PICTURE),

        /** Over-the-air (OTA) images. */
        OTA("ota", TypeDataHolder.PICTURE),

        /** Wireless bitmap. */
        WBMB("wbmb", TypeDataHolder.PICTURE),

        /** Wireless bitmap. */
        AAI("aai", TypeDataHolder.PICTURE),

        /** CALS Raster Image. */
        CAL("cal", TypeDataHolder.PICTURE),

        /** Slide Image Format. */
        SLIDE("slide", TypeDataHolder.PICTURE),

        /** Interchange File Format. */
        IFF("iff", TypeDataHolder.PICTURE),

        /** JPEG Image File. */
        JIF("jif", TypeDataHolder.PICTURE),

        /** Microsoft Paint Image. */
        MSP("msp", TypeDataHolder.PICTURE),

        /** Deluxe Paint LBM. */
        LBM("lbm", TypeDataHolder.PICTURE),

        /** InterLeaved Bitmap. */
        ILBM("ilbm", TypeDataHolder.PICTURE),

        /** CorelDRAW Template. */
        CDT("cdt", TypeDataHolder.PICTURE),

        // 音频 (Audio)

        /** MPEG-1/2 Audio Layer 3. */
        MP3("mp3", TypeDataHolder.AUDIO),

        /** Advanced Audio Codec. */
        AAC("aac", TypeDataHolder.AUDIO),

        /** Waveform Audio File Format. */
        WAV("wav", TypeDataHolder.AUDIO),

        /** Apple Lossless Audio Codec. */
        ALAC("alac", TypeDataHolder.AUDIO),

        /** Windows Media Audio. */
        WMA("wma", TypeDataHolder.AUDIO),

        /**
         * A free, open container format maintained by the Xiph.Org Foundation.
         * Its name is derived from "ogging", jargon from the computer game Netrek.
         */
        OGG("ogg", TypeDataHolder.AUDIO),

        /** Free Lossless Audio Codec. */
        FLAC("flac", TypeDataHolder.AUDIO),

        /** Opus Audio. */
        OPUS("opus", TypeDataHolder.AUDIO),

        /** Apple MP4 Audio. */
        M4A("m4a", TypeDataHolder.AUDIO),

        /** Audio Interchange File Format. */
        AIFF("aiff", TypeDataHolder.AUDIO),

        /** Adaptive Multi-Rate Codec. */
        AMR("amr", TypeDataHolder.AUDIO),

        /** Audio Interchange File Format. */
        AIF("aif", TypeDataHolder.AUDIO),

        /** True Audio. */
        TTA("tta", TypeDataHolder.AUDIO),

        /** Matroska Audio. */
        MKA("mka", TypeDataHolder.AUDIO),

        /** MIDI Sequence. */
        MIDI("midi", TypeDataHolder.AUDIO),

        /** Monkey's Audio. */
        APE("ape", TypeDataHolder.AUDIO),

        /** Digital Theater Systems. */
        DTS("dts", TypeDataHolder.AUDIO),

        /** Tom's lossless Audio Kompressor. */
        TAK("tak", TypeDataHolder.AUDIO),

        /** MusePack. */
        MPC("mpc", TypeDataHolder.AUDIO),

        /** Monkey's Audio (lossless) Codec. */
        MAC(
            "mac", TypeDataHolder.AUDIO,
            CandidateCriterion({ it.isMonkeyAudioLike() }, 10),
        ),

        /** WavePack. */
        WV("wv", TypeDataHolder.AUDIO),

        /** Audio Interchange File Compressed Format. */
        AIFC("aifc", TypeDataHolder.AUDIO),

        /** Direct Stream Digital Interchange File Format. */
        DFF("dff", TypeDataHolder.AUDIO),

        /** Direct Stream Digital Format. */
        DSF("dsf", TypeDataHolder.AUDIO),

        /** Nokia Ringing Tone. */
        NRT("nrt", TypeDataHolder.AUDIO),

        /** Pulse-code Modulation is a method used to digitally represent analog signals. */
        PCM("pcm", TypeDataHolder.AUDIO),

        /** MIDI file format, used for containing sequences of MIDI data. */
        MID("mid", TypeDataHolder.AUDIO),

        /** Protected AAC audio format used by Apple. */
        M4P("m4p", TypeDataHolder.AUDIO),

        /** Dolby Digital audio used in cinemas and home theaters. */
        AC3("ac3", TypeDataHolder.AUDIO),

        /** Free Lossless Audio Codec FLA. */
        FLA("fla", TypeDataHolder.AUDIO),

        /** Impulse Tracker module, a music tracker format. */
        IT("it", TypeDataHolder.AUDIO),

        /** Karaoke MIDI file format. */
        KAR("kar", TypeDataHolder.AUDIO),

        /** MPEG-4 Audio Book file format by Apple. */
        M4B("m4b", TypeDataHolder.AUDIO),

        /** Ringtone format for iPhone. */
        M4R("m4r", TypeDataHolder.AUDIO),

        /** Tracker module using samples and MP3 files. */
        MO3("mo3", TypeDataHolder.AUDIO),

        /** MPEG-1 Audio Layer I format. */
        MP1("mp1", TypeDataHolder.AUDIO),

        /** MPEG-1 Audio Layer II format. */
        MP2("mp2", TypeDataHolder.AUDIO),

        /** MPEG Audio Stream. */
        MPGA("mpga", TypeDataHolder.AUDIO),

        /** MultiTracker Module file format. */
        MTM("mtm", TypeDataHolder.AUDIO),

        /** Lossless audio compression format by OptimumFrog. */
        OFR("ofr", TypeDataHolder.AUDIO),

        /** Streamable variant of OptimumFrog audio files. */
        OFS("ofs", TypeDataHolder.AUDIO),

        /** Ogg Vorbis audio format for streaming. */
        OGA("oga", TypeDataHolder.AUDIO),

        /** MIDI format by Microsoft. */
        RMI("rmi", TypeDataHolder.AUDIO),

        /** Scream Tracker 3 module. */
        S3M("s3m", TypeDataHolder.AUDIO),

        /** Speech codecs used for streaming. */
        SPX("spx", TypeDataHolder.AUDIO),

        /** Unreal Engine audio file. */
        UMX("umx", TypeDataHolder.AUDIO),

        /** Extended WAV format by Sonic Foundry. */
        W64("w64", TypeDataHolder.AUDIO),

        /** Amiga Extended Module format. */
        XM("xm", TypeDataHolder.AUDIO),

        /** Audio files used on Nintendo systems. */
        AFC("afc", TypeDataHolder.AUDIO),

        /** Audio Object file on Nintendo systems. */
        AOB("aob", TypeDataHolder.AUDIO),

        /** Audibly Lossless audio format. */
        APL("apl", TypeDataHolder.AUDIO),

        /** AU audio file format by Sun Microsystems. */
        AU("au", TypeDataHolder.AUDIO),

        /** Broadcast Wave Format, an extension of WAV format. */
        BWF("bwf", TypeDataHolder.AUDIO),

        /** DTS-HD audio format used in Blu-ray. */
        DTSHD("dtshd", TypeDataHolder.AUDIO),

        /** DTS-HD Master Audio format for high definition. */
        DTSMA("dtsma", TypeDataHolder.AUDIO),

        /** DTS audio encapsulated in WAV file. */
        DTSWAV("dtswav", TypeDataHolder.AUDIO),

        /** MPEGplus Audio format, now known as MusePack. */
        MP_PLUS("mp+", TypeDataHolder.AUDIO),

        /** Musepack audio format. */
        MPP("mpp", TypeDataHolder.AUDIO),

        /** Ogg container format with video. */
        OGX("ogx", TypeDataHolder.AUDIO),

        /** Extended audio format suitable for broadcast. */
        RF64("rf64", TypeDataHolder.AUDIO),

        /** Audio format used in older Mac computers. */
        SND("snd", TypeDataHolder.AUDIO),

        /** Dolby TrueHD, a multichannel audio codec. */
        TRUEHD("truehd", TypeDataHolder.AUDIO),

        /** Simple Waveform Audio File Format. */
        WAVE("wave", TypeDataHolder.AUDIO),

        /** Core Audio Format developed by Apple. */
        CAF("caf", TypeDataHolder.AUDIO),

        /** Creative Voice File format by SoundBlaster. */
        VOC("voc", TypeDataHolder.AUDIO),

        /** Dialogic VOX ADPCM audio file. */
        VOX("vox", TypeDataHolder.AUDIO),

        /** OTS Compressed audio format. */
        OTS("ots", TypeDataHolder.AUDIO),

        /** Commodore Amiga 8-bit Sampled Voice files. */
        SVX("svx", TypeDataHolder.AUDIO),

        /** 8-bit Sound File on Amiga systems. */
        EIGHT_SVX("8svx", TypeDataHolder.AUDIO),

        /** Adaptive Multi-Rate Wideband audio codec. */
        AWB("awb", TypeDataHolder.AUDIO),

        /** Extensible Music Format, based on MIDI. */
        XMF("xmf", TypeDataHolder.AUDIO),

        /** Mobile XMF format, a variant of XMF. */
        MXMF("mxmf", TypeDataHolder.AUDIO),

        /** Modular synth file for ScreamTracker. */
        SPMID("spmid", TypeDataHolder.AUDIO),

        // 音频播放列表 (Audio playlist)

        /** MP3 URL. */
        M3U_AUDIO(
            "m3u", TypeDataHolder.AUDIO_PLAYLIST,
            CandidateCriterion({ it.isAudioM3ULike() }, 10),
            priority = 1,
        ),

        /** UTF-8 encoded MP3 URL. */
        M3U8_AUDIO(
            "m3u8", TypeDataHolder.AUDIO_PLAYLIST,
            CandidateCriterion({ it.isAudioM3ULike() }, 10),
            priority = 1,
        ),

        /** Foobar2000 playlist format. */
        FPL("fpl", TypeDataHolder.AUDIO_PLAYLIST),

        /** Multimedia playlist file used by certain media players. */
        MLP("mlp", TypeDataHolder.AUDIO_PLAYLIST),

        /** Playlist file format used by various media players. */
        PLS("pls", TypeDataHolder.AUDIO_PLAYLIST),

        /** Windows Media Audio Redirector. */
        WAX("wax", TypeDataHolder.AUDIO_PLAYLIST),

        /** Windows Media Video Redirector. */
        WVX("wvx", TypeDataHolder.AUDIO_PLAYLIST),

        /** Windows Media Player playlist format. */
        WPL("wpl", TypeDataHolder.AUDIO_PLAYLIST),

        /** XML Shareable Playlist Format used to share playlists via XML. */
        XSPF("xspf", TypeDataHolder.AUDIO_PLAYLIST),

        /** Advanced Stream Redirector used to store streaming media. */
        ASX("asx", TypeDataHolder.AUDIO_PLAYLIST),

        // 视频 (Video)

        /** MPEG-4 Part 14, widely used for storing video and audio. */
        MP4("mp4", TypeDataHolder.VIDEO),

        /** MPEG-4 Part 14, similar to MP4 but emphasizes the format specification. */
        MPEG4("mpeg4", TypeDataHolder.VIDEO),

        /** Another variation of MPEG-4 Part 14 format. */
        MPG4("mpg4", TypeDataHolder.VIDEO),

        /** Audio Video Interleave, a multimedia container format by Microsoft. */
        AVI("avi", TypeDataHolder.VIDEO),

        /** Matroska Video, known for supporting multiple video, audio, subtitle tracks. */
        MKV("mkv", TypeDataHolder.VIDEO),

        /** QuickTime Movie format, native to Apple's QuickTime player. */
        MOV("mov", TypeDataHolder.VIDEO),

        /** Windows Media Video, a series of video codecs and their corresponding video coding formats. */
        WMV("wmv", TypeDataHolder.VIDEO),

        /** Flash Video format, used primarily by Adobe Flash Player. */
        FLV("flv", TypeDataHolder.VIDEO),

        /** WebM format, designed for web streaming. */
        WEBM("webm", TypeDataHolder.VIDEO),

        /** MPEG-4 Part 14 format, used for videos on iOS devices. */
        M4V("m4v", TypeDataHolder.VIDEO),

        /** 3GPP format, used on 3G mobile phones. */
        THREE_GP("3gp", TypeDataHolder.VIDEO),

        /** MPEG video file, older format but still in use. */
        MPEG("mpeg", TypeDataHolder.VIDEO),

        /** Material Exchange Format, primarily used in professional video and broadcast. */
        MXF("mxf", TypeDataHolder.VIDEO),

        /** High efficiency video codec, commonly used for high-definition video files. */
        H264("h264", TypeDataHolder.VIDEO),

        /** MOD format, used by camcorders. */
        MOD("mod", TypeDataHolder.VIDEO),

        /** Generic video file, often used for VCD data files. */
        DAT(
            "dat", TypeDataHolder.VIDEO,
            CandidateCriterion({ it.isVcdDataLike() }, 10),
        ),

        /** Another version of H.264 video file. */
        TWO_SIX_FOUR("264", TypeDataHolder.VIDEO),

        /** 3GPP2 file, similar to 3GPP but used on CDMA mobile phones. */
        THREE_G_TWO("3g2", TypeDataHolder.VIDEO),

        /** Another format for 3GPP files. */
        THREE_GP_TWO("3gp2", TypeDataHolder.VIDEO),

        /** General 3GPP file format, used on mobile phones. */
        THREE_GPP("3gpp", TypeDataHolder.VIDEO),

        /** Blu-ray Disc Movie Format, used for Blu-ray movies. */
        BDMV("bdmv", TypeDataHolder.VIDEO),

        /** Bink Video format, used primarily for video game cutscenes. */
        BIK("bik", TypeDataHolder.VIDEO),

        /** DVR365 proprietary format, used in security cameras. */
        DAV("dav", TypeDataHolder.VIDEO),

        /** DivX format, focuses on high quality video compression. */
        DIVX("divx", TypeDataHolder.VIDEO),

        /** Digital Video format, used in professional, high-resolution recording. */
        DV("dv", TypeDataHolder.VIDEO),

        /** Enhanced VOB file format, used with HD DVDs. */
        EVO("evo", TypeDataHolder.VIDEO),

        /** MPEG-4 video file, similar to MP4 but used in Adobe Flash. */
        F4V("f4v", TypeDataHolder.VIDEO),

        /** High-definition QuickTime video file. */
        HDMOV("hdmov", TypeDataHolder.VIDEO),

        /** Indeo Video File, an older format. */
        IVF("ivf", TypeDataHolder.VIDEO),

        /** MPEG-1 video stream file. */
        M1V("m1v", TypeDataHolder.VIDEO),

        /** MPEG-2 program stream file format. */
        M2P("m2p", TypeDataHolder.VIDEO),

        /** MPEG-2 transport stream file format, used in broadcast systems. */
        M2T("m2t", TypeDataHolder.VIDEO),

        /** Blu-ray Disc Audio-Video (BDAV) MPEG-2 Transport Stream container. */
        M2TS("m2ts", TypeDataHolder.VIDEO),

        /** MPEG-2 video stream file. */
        M2V("m2v", TypeDataHolder.VIDEO),

        /** Matroska 3D Video, supports stereoscopic 3D content. */
        MK3D("mk3d", TypeDataHolder.VIDEO),

        /** MPEG-2 video file with video playback only. */
        MP2V("mp2v", TypeDataHolder.VIDEO),

        /** MPEG-4 video file. */
        MP4V("mp4v", TypeDataHolder.VIDEO),

        /** MPEG Video format, similar to MPEG but with different encoding. */
        MPE("mpe", TypeDataHolder.VIDEO),

        /** MPEG-2 video file. */
        MPV2("mpv2", TypeDataHolder.VIDEO),

        /** MPEG-4 video file with video playback only. */
        MPV4("mpv4", TypeDataHolder.VIDEO),

        /** MPEG Transport Stream video file, typically used by advanced videocams. */
        MTS("mts", TypeDataHolder.VIDEO),

        /** Ogg Media file, supports various codecs. */
        OGM("ogm", TypeDataHolder.VIDEO),

        /** RealAudio, commonly used with RealMedia videos. */
        RA("ra", TypeDataHolder.VIDEO),

        /** RealAudio Metadata format, primarily for managing streaming metadata. */
        RAM("ram", TypeDataHolder.VIDEO),

        /** Video recording file. */
        REC("rec", TypeDataHolder.VIDEO),

        /** RealMedia file, used for streaming over the internet. */
        RM("rm", TypeDataHolder.VIDEO),

        /** RealMedia Variable Bitrate file, a variant of RM with variable bitrate. */
        RMVB("rmvb", TypeDataHolder.VIDEO),

        /** Smacker Video file, used for animations and video games. */
        SMK("smk", TypeDataHolder.VIDEO),

        /** Stereoscopic interleaved Frame file format, used in Blu-ray 3D. */
        SSIF("ssif", TypeDataHolder.VIDEO),

        /** Transport Stream file, used for broadcast systems. */
        TP("tp", TypeDataHolder.VIDEO),

        /** MPEG Transport Stream file. */
        TRP("trp", TypeDataHolder.VIDEO),

        /** MPEG-2 Transport Stream video file used in broadcasting. */
        MPEG2_TS(
            "ts", TypeDataHolder.VIDEO,
            CandidateCriterion({ it.isMpeg2TsLike() }, 10),
            CandidateCriterion({ it.isSizeGreaterThan(10, SizeUnit.MB) }, 1),
            CandidateCriterion({ it.isSizeGreaterThan(100, SizeUnit.MB) }, 3),
            CandidateCriterion({ it.isSizeGreaterThan(500, SizeUnit.MB) }, 5),
        ),

        /** Generic video file format. */
        VIDEO("video", TypeDataHolder.VIDEO),

        /** DVD Video Object file, used to store the content of a DVD. */
        VOB("vob", TypeDataHolder.VIDEO),

        /** Windows Recorded TV Show file, used on Windows 7 for recording TV broadcasts. */
        WTV("wtv", TypeDataHolder.VIDEO),

        /** Advanced Systems Format, used for streaming audio and video. */
        ASF("asf", TypeDataHolder.VIDEO),

        /** Xvid Video, a format for compressed video content. */
        XVID("xvid", TypeDataHolder.VIDEO),

        /** Samsung DMB Video file, used for Korean mobile television. */
        DMSKM("dmskm", TypeDataHolder.VIDEO),

        /** Nintendo DS video format. */
        DPG("dpg", TypeDataHolder.VIDEO),

        /** Microsoft Digital Video Recording format. */
        DVR_MS("dvr-ms", TypeDataHolder.VIDEO),

        /** Enhanced AC-3, an audio codec used in broadcasting. */
        EAC3("eac3", TypeDataHolder.VIDEO),

        /** 3GPP video file used by LG phones. */
        K3G("k3g", TypeDataHolder.VIDEO),

        /** Luma MP4 video file. */
        LMP4("lmp4", TypeDataHolder.VIDEO),

        /** MPEG-1 Audio file. */
        M1A("m1a", TypeDataHolder.VIDEO),

        /** MPEG-2 Audio file. */
        M2A("m2a", TypeDataHolder.VIDEO),

        /** MPEG audio file. */
        MPA("mpa", TypeDataHolder.VIDEO),

        /** MPEG video or movie content. */
        MPG("mpg", TypeDataHolder.VIDEO),

        /** Sony QuickTime video format, similar to MOV. */
        MQV("mqv", TypeDataHolder.VIDEO),

        /** Nero ShowTime video record. */
        NSR("nsr", TypeDataHolder.VIDEO),

        /** Nullsoft Streaming Video file, used by WinAmp. */
        NSV("nsv", TypeDataHolder.VIDEO),

        /** QuickTime File Format used by Apple. */
        QT("qt", TypeDataHolder.VIDEO),

        /** Philips Video file format for certain multimedia players. */
        SKM("skm", TypeDataHolder.VIDEO),

        /** Shockwave Flash file, used for multimedia, vector graphics and ActionScript. */
        SWF("swf", TypeDataHolder.VIDEO),

        /** TMPGEnc Video Editor project. */
        TPR("tpr", TypeDataHolder.VIDEO),

        /** Windows Media, another format for Microsoft's video system. */
        WM("wm", TypeDataHolder.VIDEO),

        /** Windows Media Player format. */
        WMP("wmp", TypeDataHolder.VIDEO),

        /** MPEG video format, an older video format. */
        MVP("mvp", TypeDataHolder.VIDEO),

        // Video playlist (视频播放列表)

        /** MP3 URL. */
        M3U_VIDEO(
            "m3u", TypeDataHolder.VIDEO_PLAYLIST,
            CandidateCriterion({ it.isVideoM3ULike() }, 10),
        ),

        /** UTF-8 encoded MP3 URL. */
        M3U8_VIDEO(
            "m3u8", TypeDataHolder.VIDEO_PLAYLIST,
            CandidateCriterion({ it.isVideoM3ULike() }, 10),
        ),

        /** Media Player Classic playlist format. */
        MPCPL("mpcpl", TypeDataHolder.VIDEO_PLAYLIST),

        /** Daum PotPlayer playlist format. */
        DPL("dpl", TypeDataHolder.VIDEO_PLAYLIST),

        // 多媒体菜单 (Multimedia menu)

        /** DVD Video Object file containing menu and navigation information. */
        IFO("ifo", TypeDataHolder.MEDIA_MENU),

        /** Backup file for the IFO file, providing redundancy. */
        BUP("bup", TypeDataHolder.MEDIA_MENU),

        /** Blu-ray playlist file used for managing media sequences. */
        MPLS("mpls", TypeDataHolder.MEDIA_MENU),

        /** Media playlist file format, often used in various types of video discs. */
        MPL("mpl", TypeDataHolder.MEDIA_MENU),

        /** Blu-ray Clip Information file, detailing the content of a clip. */
        CLPI("clpi", TypeDataHolder.MEDIA_MENU),

        // CUE 脚本 (CUE sheet)

        /** Audio cue sheet describing track layout on a CD, used for playback. */
        AUDIO_CUE_SHEET(
            "cue", TypeDataHolder.AUDIO_CUE_SHEET,
            CandidateCriterion({ it.isAudioCueSheetLike() }, 10),
            priority = 1,
        ),

        /** Disk image cue sheet outlining the arrangement of data tracks, commonly used with CD images. */
        DISK_IMAGE_CUE_SHEET(
            "cue", TypeDataHolder.DISK_IMAGE_CUE_SHEET,
            CandidateCriterion({ it.isDiskImageCueSheetLike() }, 10),
        ),

        // 字幕 (Subtitle) / 歌词 (Lyrics)

        /** SMIL - Synchronized Multimedia Integration Language format. */
        SMI("smi", TypeDataHolder.SUBTITLE),

        /** Synchronized Accessible Media Interchange, often used for multimedia presentations. */
        SAMI("sami", TypeDataHolder.SUBTITLE),

        /** MicroDVD subtitle format. */
        SUB("sub", TypeDataHolder.SUBTITLE),

        /** SubStation Alpha subtitle format, supports styles and effects. */
        SSA("ssa", TypeDataHolder.SUBTITLE),

        /** Advanced SubStation Alpha, an enhanced version of SSA. */
        ASS("ass", TypeDataHolder.SUBTITLE),

        /** SubRip subtitle format, widely used for its simplicity. */
        SRT("srt", TypeDataHolder.SUBTITLE),

        /** Matroska subtitle format, associated with the MKV container. */
        MKS("mks", TypeDataHolder.SUBTITLE),

        /** MPEG-4 Timed Text subtitle format. */
        TTXT("ttxt", TypeDataHolder.SUBTITLE),

        /** AQTitle subtitle format. */
        AQT("aqt", TypeDataHolder.SUBTITLE),

        /** ASCII subtitle format. */
        ASC("asc", TypeDataHolder.SUBTITLE),

        /** DKS subtitle format, used in Korea. */
        DKS("dks", TypeDataHolder.SUBTITLE),

        /** IDX - VobSub index file, pairs with SUB files. */
        IDX("idx", TypeDataHolder.SUBTITLE),

        /** JacoSub subtitle format. */
        JSS("jss", TypeDataHolder.SUBTITLE),

        /** Lyric file format, synchronized with music. */
        LRC("lrc", TypeDataHolder.SUBTITLE),

        /** Overlay subtitle format. */
        OVR("ovr", TypeDataHolder.SUBTITLE),

        /** Panasonic subtitle format. */
        PAN("pan", TypeDataHolder.SUBTITLE),

        /** Phoenix Japanimation Society format. */
        PJS("pjs", TypeDataHolder.SUBTITLE),

        /** PowerDivX subtitle format. */
        PSB("psb", TypeDataHolder.SUBTITLE),

        /** Real-time subtitle format. */
        RT("rt", TypeDataHolder.SUBTITLE),

        /** Subtitle 2000 format. */
        S2K("s2k", TypeDataHolder.SUBTITLE),

        /** Superbase subtitle format. */
        SBT("sbt", TypeDataHolder.SUBTITLE),

        /** Subtitle script format. */
        SCR("scr", TypeDataHolder.SUBTITLE),

        /** SONY DVD subtitle format. */
        SON("son", TypeDataHolder.SUBTITLE),

        /** Spruce Technologies subtitle format. */
        SST("sst", TypeDataHolder.SUBTITLE),

        /** SST Script subtitle format. */
        SSTS("ssts", TypeDataHolder.SUBTITLE),

        /** EBU STL subtitle format, used in TV broadcasting. */
        STL(
            "stl", TypeDataHolder.SUBTITLE,
            CandidateCriterion({ it.isEbuStlLike() }, 10),
        ),

        /** European Broadcasting Union format. */
        EBU("ebu", TypeDataHolder.SUBTITLE),

        /** VobSub format extension. */
        VKT("vkt", TypeDataHolder.SUBTITLE),

        /** VSF subtitle format. */
        VSF("vsf", TypeDataHolder.SUBTITLE),

        /** ZeroG subtitle format. */
        ZEG("zeg", TypeDataHolder.SUBTITLE),

        // 加密多媒体 (Encrypted media)

        /** QQ Music encrypted media format. */
        QMC("qmc", TypeDataHolder.ENCRYPTED_MEDIA),

        /** QQ Music encrypted media variant 0. */
        QMC0("qmc0", TypeDataHolder.ENCRYPTED_MEDIA),

        /** QQ Music encrypted media variant 2. */
        QMC2("qmc2", TypeDataHolder.ENCRYPTED_MEDIA),

        /** QQ Music encrypted media variant 3. */
        QMC3("qmc3", TypeDataHolder.ENCRYPTED_MEDIA),

        /** QQ Music encrypted OGG format. */
        QMCOGG("qmcogg", TypeDataHolder.ENCRYPTED_MEDIA),

        /** QQ Music encrypted FLAC format. */
        QMCFLAC("qmcflac", TypeDataHolder.ENCRYPTED_MEDIA),

        /** QQ Music encrypted media format MGG. */
        MGG("mgg", TypeDataHolder.ENCRYPTED_MEDIA),

        /** QQ Music encrypted media format MGG1. */
        MGG1("mgg1", TypeDataHolder.ENCRYPTED_MEDIA),

        /** QQ Music encrypted FLAC format MFLAC. */
        MFLAC("mflac", TypeDataHolder.ENCRYPTED_MEDIA),

        /** QQ Music encrypted FLAC variant MFLAC0. */
        MFLAC0("mflac0", TypeDataHolder.ENCRYPTED_MEDIA),

        /** QQ Music encrypted MP3 format BKCMP3. */
        BKCMP3("bkcmp3", TypeDataHolder.ENCRYPTED_MEDIA),

        /** QQ Music encrypted FLAC format BKCFLAC. */
        BKCFLAC("bkcflac", TypeDataHolder.ENCRYPTED_MEDIA),

        /** QQ Music encrypted media format TKM. */
        TKM("tkm", TypeDataHolder.ENCRYPTED_MEDIA),

        /** NetEase encrypted media format. */
        NCM("ncm", TypeDataHolder.ENCRYPTED_MEDIA),

        /** Kugou Music encrypted media format KGG. */
        KGG("kgg", TypeDataHolder.ENCRYPTED_MEDIA),

        /** Kugou Music encrypted media format KGM. */
        KGM("kgm", TypeDataHolder.ENCRYPTED_MEDIA),

        /** Kugou Music encrypted media format KGMA. */
        KGMA("kgma", TypeDataHolder.ENCRYPTED_MEDIA),

        // 编译 (Compiled) / 二进制 (Binary)

        /** Java class file compiled from Java source code. */
        CLASS("class", TypeDataHolder.COMPILE),

        /** Dalvik Executable file, used for Android applications. */
        DEX("dex", TypeDataHolder.COMPILE),

        /** Smali file, representing disassembled Android bytecode. */
        SMALI("smali", TypeDataHolder.COMPILE),

        /** Compiled Python file created from Python source code. */
        PYC("pyc", TypeDataHolder.COMPILE),

        /** Application binary file, often seen in mobile apps. */
        APP("app", TypeDataHolder.COMPILE),

        /** Compiled resource file. */
        RSC("rsc", TypeDataHolder.COMPILE),

        /** Object file, typically used in the context of compiled code. */
        OBJ("obj", TypeDataHolder.COMPILE, priority = 3),

        /** ARM Executable Format used for ARM architecture. */
        AXF("axf", TypeDataHolder.COMPILE),

        /** Executable and Linkable Format, common in Unix-like systems. */
        ELF("elf", TypeDataHolder.COMPILE),

        /** Update binary, often used in firmware updates. */
        UPDATE_BINARY("update-binary", TypeDataHolder.COMPILE),

        /** Generic binary file format. */
        BINARY("bin", TypeDataHolder.COMPILE),

        /** Kernel object file, used for loadable kernel modules. */
        KERNEL_OBJECT("ko", TypeDataHolder.COMPILE),

        // 库 (Library)

        /** Dynamic Link Library, commonly used in Windows environments. */
        DLL("dll", TypeDataHolder.COMPILE),

        /** Static library file, used in various programming languages. */
        LIB("lib", TypeDataHolder.COMPILE),

        // 共享库 (Shared Object)

        /** Shared object file, used for dynamic linking in Unix-like systems. */
        SO("so", TypeDataHolder.COMPILE),

        // 数据 (Data) / 数据库 (Database)

        /** vCard format, used for storing contact information. */
        VCF("vcf", TypeDataHolder.TEXT),

        /** vCalendar file format, for calendar data interchange. */
        VCALENDAR("vcalendar", TypeDataHolder.TEXT),

        /** vCalendar file format, used for calendar events. */
        VCS("vcs", TypeDataHolder.TEXT),

        /** iCalendar format, widely used for sharing calendaring events. */
        ICALENDAR("icalendar", TypeDataHolder.TEXT),

        /** iCalendar file format extension for calendar data. */
        ICAL("ical", TypeDataHolder.TEXT),

        /** iCalendar standard format, used for date and time information. */
        ICS("ics", TypeDataHolder.TEXT),

        /** iCalendar free/busy information format. */
        IFB("ifb", TypeDataHolder.TEXT),

        /** Dictionary file, stores word definitions and data. */
        DIC("dic", TypeDataHolder.DATA),

        /** General data file format, can include various types of data. */
        GENERATE_DATA(
            "dat", TypeDataHolder.DATA,
            CandidateCriterion({ it.isGenerateDataLike() }, 10),
            CandidateCriterion({ it.isSizeLessThan(1, SizeUnit.KB) }, 1),
            priority = 1,
        ),

        /** Comma-separated values, a simple spreadsheet format. */
        CSV("csv", TypeDataHolder.DATA),

        /** Tab-separated values, similar to CSV but uses tabs. */
        TSV("tsv", TypeDataHolder.DATA),

        /** Microsoft Excel spreadsheet file format. */
        XLS("xls", TypeDataHolder.DATA),

        /** Microsoft Excel Open XML spreadsheet format. */
        XLSX("xlsx", TypeDataHolder.DATA),

        /** OpenDocument Spreadsheet format used in office suites. */
        ODS("ods", TypeDataHolder.DATA),

        /** SQL file, commonly used for storing database queries. */
        SQL("sql", TypeDataHolder.DATA),

        /** SQLite database file, a self-contained, serverless database. */
        SQLITE("sqlite", TypeDataHolder.DATA),

        /** Database file, generic format for storing structured data. */
        DB("db", TypeDataHolder.DATA),

        /** Database journal file, used for transaction tracking. */
        DB_JOURNAL("db-journal", TypeDataHolder.DATA_RELATED),

        /** Shared Memory file for SQLite databases. */
        DB_SHM("db-shm", TypeDataHolder.DATA_RELATED),

        /** Write-ahead log file for SQLite databases. */
        DB_WAL("db-wal", TypeDataHolder.DATA_RELATED),

        /** FreeFileSync database file, used for synchronization. */
        FFS_DB("ffs_db", TypeDataHolder.DATA),

        /** GraphQL file, used for expressing server-side logic. */
        GRAPH_QL("graphql", TypeDataHolder.DATA),

        /** GraphQL query file extension. */
        GQL("gql", TypeDataHolder.DATA),

        /** SQL Server query file, a shortcut for running queries. */
        DQY("dqy", TypeDataHolder.DATA),

        /** Internet Query file format, for web data queries. */
        IQY("iqy", TypeDataHolder.DATA),

        /** Office Data Connection file, links spreadsheet to external data. */
        ODC("odc", TypeDataHolder.DATA),

        /** Symbolic Link File format, used for data exchange. */
        SLK("slk", TypeDataHolder.DATA),

        /** Excel Add-in file. */
        XLA("xla", TypeDataHolder.DATA),

        /** Excel Macro-Enabled Add-In. */
        XLAM("xlam", TypeDataHolder.DATA),

        /** Excel backup file. */
        XLK("xlk", TypeDataHolder.DATA),

        /** Excel Add-In file for linking code to spreadsheets. */
        XLL("xll", TypeDataHolder.DATA),

        /** Excel Macro sheet. */
        XLM("xlm", TypeDataHolder.DATA),

        /** Binary Excel file extension used for storing data. */
        XLXB("xlxb", TypeDataHolder.DATA),

        /** HTML file linked to Excel spreadsheet data. */
        XLSHTML("xlshtml", TypeDataHolder.DATA),

        /** Macro-enabled Excel spreadsheet. */
        XLSM("xlsm", TypeDataHolder.DATA),

        /** Excel template file. */
        XLT("xlt", TypeDataHolder.DATA),

        /** HTML linked Excel template. */
        XLTHTML("xlthtml", TypeDataHolder.DATA),

        /** Macro-enabled Excel template. */
        XLTM("xltm", TypeDataHolder.DATA),

        /** Excel Open XML template format. */
        XLTX("xltx", TypeDataHolder.DATA),

        /** Excel workspace file. */
        XLW("xlw", TypeDataHolder.DATA),

        /** SPSS data file, used for statistical analysis. */
        SAV("sav", TypeDataHolder.DATA),

        /** Memory card save file for PlayStation emulators. */
        MCR("mcr", TypeDataHolder.DATA),

        /** Microsoft Access database file. */
        MDB("mdb", TypeDataHolder.DATA),

        /** MySQL data file, used to store tables. */
        MYD("myd", TypeDataHolder.DATA),

        /** MySQL index file, used for indexes of tables. */
        MYI("myi", TypeDataHolder.DATA),

        /** JasperReports XML file, defines reports and layout. */
        JRXML("jrxml", TypeDataHolder.DATA),

        /** Palm Database file, used on Palm OS devices. */
        PDB("pdb", TypeDataHolder.DATA),

        /** CTR Executable Image format, used for raw game data on 3DS. */
        CXI("cxi", TypeDataHolder.DATA),

        // 压缩 (Compressed)

        /** ZIP Archive, widely used for data compression. */
        ZIP("zip", TypeDataHolder.ARCHIVE),

        /** ZIP Archive extended format, used for high-compression ZIP files. */
        ZIPX("zipx", TypeDataHolder.ARCHIVE),

        /** WebJetFile Archive, used for compressed file archives. */
        WJF("wjf", TypeDataHolder.ARCHIVE),

        /** RAR Archive, known for efficient data compression. */
        RAR("rar", TypeDataHolder.ARCHIVE),

        /** Self-extracting Archive, often a variant of RAR or ZIP. */
        SFX("sfx", TypeDataHolder.ARCHIVE),

        /** 7-Zip Archive, an open-source high-compression format. */
        SEVEN_ZIP("7z", TypeDataHolder.ARCHIVE),

        /** Part 1 of a split archive, used for segmented archives. */
        ZERO_ZERO_ONE("001", TypeDataHolder.ARCHIVE),

        /** Numeric scheme for consecutive split archive parts. */
        ONE("1", TypeDataHolder.ARCHIVE),

        /** ACE Archive, sometimes used in the shareware community. */
        ACE("ace", TypeDataHolder.ARCHIVE),

        /** AES encryption file, used for secure file storage. */
        AES("aes", TypeDataHolder.ARCHIVE),

        /** ALZip Archive, used by ALZip software for data compression. */
        ALZ("alz", TypeDataHolder.ARCHIVE),

        /** ARC file, an older format for compressed archives. */
        ARC("arc", TypeDataHolder.ARCHIVE),

        /** ARJ file, a compressed archive format often used in DOS. */
        ARJ("arj", TypeDataHolder.ARCHIVE),

        /** Black Hole file, used by the Black Hole archiving tool. */
        BH("bh", TypeDataHolder.ARCHIVE),

        /** Brotli compressed file, used for data compression. */
        BR("br", TypeDataHolder.ARCHIVE),

        /** Bzip compress file, a format for compressing data. */
        BZ("bz", TypeDataHolder.ARCHIVE),

        /** Bzip2, an open-source compress format with higher compression. */
        BZ2("bz2", TypeDataHolder.ARCHIVE),

        /** Cabinet file, used by Microsoft for software distribution. */
        CAB("cab", TypeDataHolder.ARCHIVE),

        /** EGG Archive, used for compressing files in Korea. */
        EGG("egg", TypeDataHolder.ARCHIVE),

        /** Gzip Archive, a file format for file compression. */
        GZ("gz", TypeDataHolder.ARCHIVE),

        /** Gzip, GNU zipped software, used for compressing files. */
        GZIP("gzip", TypeDataHolder.ARCHIVE),

        /** LZH Archived file, often used in Japan for compressing files. */
        LHA("lha", TypeDataHolder.ARCHIVE),

        /** Lzip compressed file, using LZ77 algorithm for compression. */
        LZ("lz", TypeDataHolder.ARCHIVE),

        /** LZ4 compressed file, known for very fast compression and decompression. */
        LZ4("lz4", TypeDataHolder.ARCHIVE),

        /** LZH compressed file, an older compression format. */
        LZH("lzh", TypeDataHolder.ARCHIVE),

        /** LZMA compressed file, providing higher compression ratios. */
        LZMA("lzma", TypeDataHolder.ARCHIVE),

        /** PEA Archive, format including compression, multi-volume and encryption. */
        PEA("pea", TypeDataHolder.ARCHIVE),

        /** PMA Archive, a file format for further compression purposes. */
        PMA("pma", TypeDataHolder.ARCHIVE),

        /** TAR Archive, often used for file packages in UNIX/LINUX. */
        TAR("tar", TypeDataHolder.ARCHIVE),

        /** TAR BZIP, a tarball compressed with Bzip. */
        TBZ("tbz", TypeDataHolder.ARCHIVE),

        /** TAR BZIP2, a tarball compressed with Bzip2. */
        TBZ2("tbz2", TypeDataHolder.ARCHIVE),

        /** TAR GZIP, a tarball compressed with Gzip. */
        TGZ("tgz", TypeDataHolder.ARCHIVE),

        /** TAR LZ, a tarball compressed with Lzip. */
        TLZ("tlz", TypeDataHolder.ARCHIVE),

        /** TAR XZ, a tarball compressed with XZ. */
        TXZ("txz", TypeDataHolder.ARCHIVE),

        /** UUEncode File, used for converting binary data to text. */
        UU("uu", TypeDataHolder.ARCHIVE),

        /** UUE, Unix-to-Unix Encoding, another form of UUEncode. */
        UUE("uue", TypeDataHolder.ARCHIVE),

        /** XXEncode file, similar to UUencode, used for encoding binaries. */
        XXE("xxe", TypeDataHolder.ARCHIVE),

        /** XZ compressed file, known for high compression ratio. */
        XZ("xz", TypeDataHolder.ARCHIVE),

        /** Unix Compress file, an older method for compressing files. */
        Z("z", TypeDataHolder.ARCHIVE),

        /** ZPAQ Archive, offering advanced compression capabilities. */
        ZPAQ("zpaq", TypeDataHolder.ARCHIVE),

        /** Zstandard compressed file, a high-performance real-time compression algorithm. */
        ZST("zst", TypeDataHolder.ARCHIVE),

        /** JAR Archive, Java Archive for packaging Java classes and resources. */
        JAR("jar", TypeDataHolder.ARCHIVE),

        /** Android Archive, used in Android development to package Android libraries. */
        AAR("aar", TypeDataHolder.ARCHIVE),

        /** TAR Z compressed file, a tarball compressed with Z. */
        TAZ("taz", TypeDataHolder.ARCHIVE),

        /** TAR Zstandard compressed file, a tarball compressed with Zstandard. */
        TZST("tzst", TypeDataHolder.ARCHIVE),

        /** Android OBB file, used to package large media files in apps. */
        OBB("obb", TypeDataHolder.ARCHIVE),

        /** Binary Archive file, a generic format for binary archives. */
        BIN_ARCHIVE(
            "bin", TypeDataHolder.ARCHIVE,
            CandidateCriterion({ it.isBinArchiveLike() }, 7),
        ),

        /** Web Application Archive, used to distribute a collection of JAR files. */
        WAR("war", TypeDataHolder.ARCHIVE),

        /** CPIO Archive, used for archiving Unix files. */
        CPIO("cpio", TypeDataHolder.ARCHIVE),

        // 镜像 (Disk image)

        /** ISO Image, a popular format for optical disk images. */
        ISO("iso", TypeDataHolder.DISK_IMAGE),

        /** IMG file, often a raw disk image file format. */
        IMG("img", TypeDataHolder.DISK_IMAGE),

        /** ISZ Image, a compressed ISO image format. */
        ISZ("isz", TypeDataHolder.DISK_IMAGE),

        /** NRG file, proprietary disk image format used by Nero Burning ROM. */
        NRG("nrg", TypeDataHolder.DISK_IMAGE),

        /** MDS file, often used with optical disc images for metadata. */
        MDS("mds", TypeDataHolder.DISK_IMAGE),

        /** MDF file, the actual data of a disc from the MDS descriptor. */
        MDF("mdf", TypeDataHolder.DISK_IMAGE),

        /** Binary Disk Image, an older format often coupled with a CUE file. */
        BIN_DISK_IMAGE(
            "bin", TypeDataHolder.DISK_IMAGE,
            CandidateCriterion({ it.isBinDiscImageLike() }, 7),
            CandidateCriterion({ it.withSameNameExtension("cue") }, 10),
            CandidateCriterion({ it.isSizeGreaterThan(500, SizeUnit.MB) }, 1),
        ),

        /** Virtual CD, used to emulate CD drives. */
        VCD("vcd", TypeDataHolder.DISK_IMAGE),

        /** LCD file, a LaserDisc image format. */
        LCD("lcd", TypeDataHolder.DISK_IMAGE),

        /** FCD file, a proprietary disk image format used by Virtual CD. */
        FCD("fcd", TypeDataHolder.DISK_IMAGE),

        /** Compact Disc Descriptor, used with CloneCD images for metadata. */
        CCD("ccd", TypeDataHolder.DISK_IMAGE),

        // 绘图 (Drawing) / 模型 (Modeling)

        /** HPGL Plotter file, used by HP graphics plotters. */
        PLT("plt", TypeDataHolder.DRAWING),

        /** Open Neural Network Exchange, used for neural network models. */
        ONNX("onnx", TypeDataHolder.DRAWING),

        /** CAD file, a generic term for Computer-Aided Design files. */
        CAD("cad", TypeDataHolder.DRAWING),

        /** Drawing file in AutoCAD, used for storing 2D and 3D design data. */
        DWG("dwg", TypeDataHolder.DRAWING),

        /** Drawing Exchange Format, used for CAD data exchange. */
        DXF("dxf", TypeDataHolder.DRAWING),

        /** ACIS Text file, used for 3D modeling. */
        ACIS("sat", TypeDataHolder.DRAWING),

        /** ACIS Binary file format, a binary version of SAT for 3D data. */
        ACIS_BINARY("sab", TypeDataHolder.DRAWING),

        /** Parasolid Text Transmittal file, used in 3D modeling. */
        PARASOLID_TEXT_TRANSMITTAL("x_t", TypeDataHolder.DRAWING),

        /** Parasolid Binary file, a format for exchanging 3D CAD data. */
        PARASOLID_BINARY("x_b", TypeDataHolder.DRAWING),

        /** SolidWorks Part file, used in the SolidWorks CAD software. */
        SOLID_WORKS_PART("sldprt", TypeDataHolder.DRAWING),

        /** STEP file, an ISO standard for exchanging product model data. */
        STEP("step", TypeDataHolder.DRAWING),

        /** Alternative extension for STEP files. */
        STP("stp", TypeDataHolder.DRAWING),

        /** Initial Graphics Exchange Specification, used for CAD models. */
        IGES("iges", TypeDataHolder.DRAWING),

        /** Alternative extension for IGES files. */
        IGS("igs", TypeDataHolder.DRAWING),

        /** Boundary Representation files, used in CAD design. */
        BREP("brep", TypeDataHolder.DRAWING),

        /** Stereolithography file, used for 3D printing and modeling. */
        STEREOLITHOGRAPHY(
            "stl", TypeDataHolder.DRAWING,
            CandidateCriterion({ it.isModelStlLike() }, 10),
            priority = 4,
        ),

        /** Floor Plan Projection file, used in architectural design for detailed floor layouts. */
        FPP("fpp", TypeDataHolder.DRAWING),

        /** Inventor Part file, used in Autodesk Inventor. */
        IPT("ipt", TypeDataHolder.DRAWING),

        /** Industry Foundation Classes file, used for building models. */
        IFC("ifc", TypeDataHolder.DRAWING),

        /** Wavefront OBJ file, used for 3D models. */
        WAVEFRONT_OBJ(
            "obj", TypeDataHolder.DRAWING,
            CandidateCriterion({ it.isWavefrontObjLike() }, 10),
        ),

        /** Autodesk 3ds Max file, a format for 3D modeling. */
        THREE_DS(
            "3ds", TypeDataHolder.DRAWING,
            CandidateCriterion({ it.isModel3dsLike() }, 10),
        ),

        /** Digital Asset Exchange, used for sharing digital assets. */
        DAE("dae", TypeDataHolder.DRAWING),

        /** Blender file, used for storing 3D models and animations. */
        BLEND("blend", TypeDataHolder.DRAWING),

        /** Filmbox file, used for 3D models, widely used for 3D animation. */
        FBX("fbx", TypeDataHolder.DRAWING),

        /** Material library file for OBJ models. */
        MTL("mtl", TypeDataHolder.DRAWING),

        /** 3ds Max Scene file. */
        MAX("max", TypeDataHolder.DRAWING),

        /** Maya ASCII file, used for storing 3D models in Maya. */
        MA("ma", TypeDataHolder.DRAWING),

        /** Cinema 4D file, used in 3D modeling. */
        C4D("c4d", TypeDataHolder.DRAWING),

        /** LightWave 3D file, used for storing 3D models. */
        LXO("lxo", TypeDataHolder.DRAWING),

        /** ZBrush Project file, used for 3D and sculpting. */
        ZBP("zbp", TypeDataHolder.DRAWING),

        /** ZBrush Tool file, used for digital sculpting. */
        ZTL("ztl", TypeDataHolder.DRAWING),

        // 安卓程序包 (Android packages)

        /** Android Package, the file format for apps on Android. */
        APK("apk", TypeDataHolder.APK),

        /** Android Package with number, e.g., "apk.1". */
        APK_WITH_NUMBER(withRegexPrefix { "apk\\.\\d+" }, TypeDataHolder.APK),

        /** Android Split APKs, used for app bundles. */
        APKS("apks", TypeDataHolder.APK_EXT),

        /** Extended APK Package, includes additional resources. */
        XAPK("xapk", TypeDataHolder.APK_EXT),

        /** Android App Bundle, used for distributing Android apps. */
        AAB("aab", TypeDataHolder.APK_EXT),

        // 其他程序包 (Other packages)

        /** iOS App Store Package, the format for iOS applications. */
        IPA("ipa", TypeDataHolder.EXECUTABLE),

        /** Debian Package, used for installing software on Debian-based systems. */
        DEB("deb", TypeDataHolder.EXECUTABLE),

        /** Red Hat Package Manager file, used in Red Hat-based systems. */
        RPM("rpm", TypeDataHolder.EXECUTABLE),

        /** Symbian Installation System, used for Symbian OS applications. */
        SIS("sis", TypeDataHolder.EXECUTABLE),

        /** Symbian OS Package, an extended format for Symbian OS apps. */
        SISX("sisx", TypeDataHolder.EXECUTABLE),

        // 固件 (Firmware)

        /** iOS Firmware file, used to install or restore iOS devices. */
        IPSW("ipsw", TypeDataHolder.FIRMWARE),

        /** Sony FlashTool Firmware file, used for updating firmware on Sony devices. */
        FTF("ftf", TypeDataHolder.FIRMWARE),

        /** PlayStation Portable Firmware Package, used for PSP games. */
        PBP("pbp", TypeDataHolder.FIRMWARE),

        // 可执行程序 (Executable programs)

        /** Executable file for Windows. */
        EXE("exe", TypeDataHolder.EXECUTABLE),

        /** Microsoft Installer Package, used for installing software on Windows. */
        MSI("msi", TypeDataHolder.EXECUTABLE),

        /** Backup file, but used here as an executable format. */
        BAC("bac", TypeDataHolder.EXECUTABLE),

        /** Borland Package Library, used in Delphi applications. */
        BPL("bpl", TypeDataHolder.EXECUTABLE),

        /** COM file, a legacy format for executables. */
        COM("com", TypeDataHolder.EXECUTABLE),

        /** CTR Importable Archive file, used by Nintendo 3DS for installations. */
        CIA("cia", TypeDataHolder.EXECUTABLE),

        /** Homebrew application format for the Nintendo Switch. */
        NRO("nro", TypeDataHolder.EXECUTABLE),

        // 配置 (Configuration)

        /** Generic configuration file. */
        CONFIG("config", TypeDataHolder.CONFIG),

        /** Configuration file, another common format. */
        CONF("conf", TypeDataHolder.CONFIG),

        /** Configuration file, shorthand for "configuration." */
        CFG("cfg", TypeDataHolder.CONFIG),

        /** INI file, used for configuration in Windows. */
        INI("ini", TypeDataHolder.CONFIG),

        /** Language file, often configurations for localization. */
        LNG("lng", TypeDataHolder.CONFIG),

        /** INF file, used for configuration in installing Windows drivers. */
        INF("inf", TypeDataHolder.CONFIG),

        /** Java properties file, used to store configuration data. */
        PROPERTIES("properties", TypeDataHolder.CONFIG),

        /** Makefile, used in build automation. */
        MAKE_FILE("makefile", TypeDataHolder.CONFIG),

        /** CMake file, used to control the software compilation process. */
        CMAKE("cmake", TypeDataHolder.CONFIG),

        /** Makefile, used in build automation through a different naming. */
        MK("mk", TypeDataHolder.CONFIG),

        /** Makefile specifically used with different build systems. */
        MAK("mak", TypeDataHolder.CONFIG),

        /** ProGuard configuration file, used for configuring Java obfuscation processes. */
        PROGUARD(
            "pro", TypeDataHolder.CONFIG,
            CandidateCriterion({ it.isProguardConfigLike() }, 10),
            priority = 2,
        ),

        /** Mobile Information Device Profile configuration file. */
        MIDP("midp", TypeDataHolder.CONFIG),

        /** System or game configuration file for PSP. */
        SFO("sfo", TypeDataHolder.CONFIG),

        /** QMake project file, used in the Qt framework for defining project-wide build configurations. */
        QMAKE_PROJECT(
            "pro", TypeDataHolder.CONFIG,
            CandidateCriterion({ it.isQmakeProjectLike() }, 10),
        ),

        // 证书 (Certificate)

        /** Dot-zero file, often used to denote a certificate in a sequence of certification files. */
        ZERO("0", TypeDataHolder.CERTIFICATE),

        /** Certificate authority data in the form of text or DER encoded format. */
        CRT("crt", TypeDataHolder.CERTIFICATE),

        /** Certificate file, often used interchangeably with .crt. */
        CER("cer", TypeDataHolder.CERTIFICATE),

        /** Distinguished Encoding Rules, a binary encoding for certificates. */
        DER("der", TypeDataHolder.CERTIFICATE),

        /** Certificate Revocation List, listing revoked certificates. */
        CRL("crl", TypeDataHolder.CERTIFICATE),

        /** Private key file, storing unencrypted/private SSH keys. */
        KEY("key", TypeDataHolder.CERTIFICATE),

        /** Java KeyStore file, used to store certificates and private keys. */
        JAVA_KEYSTORE("jks", TypeDataHolder.CERTIFICATE),

        /** Generic certificate key file, format may vary. */
        GXK("gxk", TypeDataHolder.CERTIFICATE),

        /** SSH keys for Secure Shell authentication and encryption. */
        SSH("ssh", TypeDataHolder.CERTIFICATE),

        /** Public key file, for systems using public/private key pairs. */
        PUB("pub", TypeDataHolder.CERTIFICATE),

        /** PuTTY Private Key file, used by PuTTY SSH client. */
        PPK("ppk", TypeDataHolder.CERTIFICATE),

        /** PKCS #7 file, a container file for certificates. */
        P7B("p7b", TypeDataHolder.CERTIFICATE),

        /** PKCS #7 Cryptographic Message Syntax Standard. */
        P7C("p7c", TypeDataHolder.CERTIFICATE),

        /** PKCS #12, often used for storing the certificate chain and key. */
        P12("p12", TypeDataHolder.CERTIFICATE),

        /** Privacy-Enhanced Mail, a base64 encoded format for certificates. */
        PEM("pem", TypeDataHolder.CERTIFICATE),

        /** Personal Information Exchange, a PKCS #12 file. */
        PFX("pfx", TypeDataHolder.CERTIFICATE),

        /** Encrypted certificate file, the specific format may vary. */
        AXX("axx", TypeDataHolder.CERTIFICATE),

        /** Encrypted authentication or access file. */
        EEA("eea", TypeDataHolder.CERTIFICATE),

        /** Trusted Certificate file format. */
        TC("tc", TypeDataHolder.CERTIFICATE),

        /** Generic encoded or encrypted certificate file. */
        KODE("kode", TypeDataHolder.CERTIFICATE),

        /** Encrypted password file, often related to certificates. */
        BPW("bpw", TypeDataHolder.CERTIFICATE),

        /** Key Database file, used by some software to store keys. */
        KDB("kdb", TypeDataHolder.CERTIFICATE),

        /** KDBX format, commonly associated with password managers. */
        KDBX("kdbx", TypeDataHolder.CERTIFICATE),

        // 签名 (Signature)

        /** Signature file, used to verify the authenticity of a file. */
        SIG("sig", TypeDataHolder.SIGNATURE),

        // 游戏 (Games)

        /** Game Boy ROM file, used for Nintendo Game Boy games. */
        GB("gb", TypeDataHolder.GAME),

        /** Game Boy Color ROM file, specifically for Game Boy Color games. */
        GBC("gbc", TypeDataHolder.GAME),

        /** Game Boy Advance ROM file, used for GBA games. */
        GBA("gba", TypeDataHolder.GAME),

        /** Nintendo Entertainment System ROM file. */
        NES("nes", TypeDataHolder.GAME),

        /** Neo Geo ROM file, used for Neo Geo games. */
        NEO("neo", TypeDataHolder.GAME),

        /** Nintendo 3DS game ROM file. */
        NINTENDO_3DS(
            "3ds", TypeDataHolder.GAME,
            CandidateCriterion({ it.isNintendo3dsLike() }, 10),
        ),

        /** Similar to `.3ds` but contains online play card ID. */
        NINTENDO_3DZ("3dz", TypeDataHolder.GAME),

        /** Nintendo DS ROM file, used for NDS games. */
        NDS("nds", TypeDataHolder.GAME),

        /** Nintendo DS ROM data file, alternative extension. */
        DS("ds", TypeDataHolder.GAME),

        /** Super Nintendo Entertainment System ROM file. */
        SMC("smc", TypeDataHolder.GAME),

        /** Super Famicom ROM file, similar to SNES ROMs. */
        SFC("sfc", TypeDataHolder.GAME),

        /** Super Nintendo ROM file, alternative to SMC. */
        FIG("fig", TypeDataHolder.GAME),

        /** Compressed ISO, a format for PSP game files. */
        CSO("cso", TypeDataHolder.GAME),

        /** Smart Game Format, used for storing game records. */
        SGF("sgf", TypeDataHolder.GAME),

        /** Super Nintendo Entertainment System, alternative ROM file. */
        SNES("snes", TypeDataHolder.GAME),

        /** Nintendo 64 ROM file, used for N64 games. */
        N64("n64", TypeDataHolder.GAME),

        /** Alternative format for Nintendo 64 ROMs. */
        Z64("z64", TypeDataHolder.GAME),

        /** Sega Genesis/Mega Drive ROM file. */
        GEN("gen", TypeDataHolder.GAME),

        /** Sega Mega Drive ROM file, identified as such through custom criteria. */
        SEGA_MD(
            "md", TypeDataHolder.GAME,
            CandidateCriterion({ it.isSegaMDLike() }, 10),
        ),

        /** Sega Mega Drive ROM file, alternative extension. */
        SMD("smd", TypeDataHolder.GAME),

        /** Nintendo Switch game ROM file, typically a raw cartridge image. */
        XCI("xci", TypeDataHolder.GAME),

        /** Nintendo Submission Package for Nintendo Switch, used for eShop apps/games. */
        NSP("nsp", TypeDataHolder.GAME),

        /** Compressed Nintendo Submission Package for smaller file size storage. */
        NSZ("nsz", TypeDataHolder.GAME),

        // 其他 (Others)

        /** MSBuild Response file, used to pass command line arguments to MSBuild. */
        RSP("rsp", TypeDataHolder.OTHERS),

        /** Security Catalog, used to verify the integrity and authenticity of Windows files. */
        CAT("cat", TypeDataHolder.OTHERS),

        /** Session Description Protocol, used to describe multimedia sessions. */
        SDP("sdp", TypeDataHolder.TEXT),

        /** Java Application Descriptor, defines properties for Java ME applications. */
        JAD("jad", TypeDataHolder.TEXT),

        /** Description in Zip, often a small text file included in .zip archives. */
        DIZ("diz", TypeDataHolder.TEXT),

        /** CHK file, fragments of files recovered by CHKDSK or similar utilities. */
        CHK("chk", TypeDataHolder.OTHERS),

        /** Thumbnail file, typically used to store thumbnail images. */
        THUMB("thumb", TypeDataHolder.OTHERS),

        /** System file, used by operating systems to define hardware configurations. */
        SYS("sys", TypeDataHolder.OTHERS),

        /** Backup file, holds copies of important data for restoration. */
        BACKUP("backup", TypeDataHolder.OTHERS),

        /** Backup file, often created automatically by applications. */
        BAK("bak", TypeDataHolder.OTHERS),

        /** Short-form backup file, similar purpose to .bak. */
        BK("bk", TypeDataHolder.OTHERS),

        /** Version file, stores version information about software or documents. */
        VERSION("version", TypeDataHolder.TEXT),

        /** Temporary file, typically used for short-term storage during program execution. */
        TMP("tmp", TypeDataHolder.OTHERS),

        /** ICC Profile, used for device color profile settings. */
        ICC("icc", TypeDataHolder.OTHERS),

        /** ICM file, another format for ICC color profiles. */
        ICM("icm", TypeDataHolder.OTHERS),

        /** ActiveX Control file, used in Microsoft's COM architecture. */
        OCX("ocx", TypeDataHolder.OTHERS),

        /** AGO file, possibly associated with animations or graphics. */
        AGO("ago", TypeDataHolder.OTHERS),

        /** TNT file, possibly used in specialized applications or games. */
        TNT("tnt", TypeDataHolder.OTHERS),

        /** Package file, used for software distribution or installation. */
        PKG("pkg", TypeDataHolder.OTHERS),

        /** Windows Registry file, used to modify the Windows registry settings. */
        REG("reg", TypeDataHolder.TEXT),

        /** Remote Desktop Protocol file, used to connect to remote Windows desktops. */
        RDP("rdp", TypeDataHolder.OTHERS),

        /** Printer file, associated with printing configurations. */
        PRN("prn", TypeDataHolder.OTHERS),

        /** XSL Formatting Objects file, used for XML transformations. */
        FO("fo", TypeDataHolder.OTHERS),

        /** Microsoft Project database file, used for project management data. */
        MPD("mpd", TypeDataHolder.OTHERS),

        /** Outlook Message file, representing an email message. */
        MSG("msg", TypeDataHolder.OTHERS),

        /** Email file format, used to store or exchange emails. */
        EML("eml", TypeDataHolder.TEXT),

        /** Microsoft Outlook Personal Folder File, stores emails and other data. */
        PST("pst", TypeDataHolder.OTHERS),

        /** ASP.NET Master Page, used for web application templating. */
        MASTER("master", TypeDataHolder.TEXT),

        /** ASP.NET User Control file, used in web application development. */
        ASCX("ascx", TypeDataHolder.TEXT),

        /** .htaccess file, used for Apache web server configuration. */
        HTACCESS("htaccess", TypeDataHolder.TEXT),

        /** Visual effects file, possibly used in animation or graphic software. */
        VFX("vfx", TypeDataHolder.OTHERS),

        /** Visual format template file, potentially related to graphics applications. */
        VFT("vft", TypeDataHolder.OTHERS),

        /** Export file, potentially used in accounting or data transfer applications. */
        EXP("exp", TypeDataHolder.OTHERS),

        /** UFO file, possibly used for design or multimedia projects. */
        UFO("ufo", TypeDataHolder.OTHERS),

        /** Animated cursor file, used by Windows for custom cursors. */
        ANI("ani", TypeDataHolder.OTHERS),

        /** Management Information Base file, used in network configurations. */
        MIB("mib", TypeDataHolder.OTHERS),

        /** Lexicon file, used by linguistic or language applications. */
        LEX("lex", TypeDataHolder.OTHERS),

        /** Input Method Editor file, used for multilingual input settings. */
        IME("ime", TypeDataHolder.OTHERS),

        /** Image data file, possibly used by specific applications. */
        IMD("imd", TypeDataHolder.OTHERS),

        /** National Language Support file, used for localization. */
        NLS("nls", TypeDataHolder.OTHERS),

        /** PostScript Printer Description file, used for printer configuration. */
        PPD("ppd", TypeDataHolder.TEXT),

        /** General Description Language file, potentially used in technical documents. */
        GDL("gdl", TypeDataHolder.TEXT),

        /** Generic Printer Description file, used in printing configurations. */
        GPD("gpd", TypeDataHolder.TEXT),

        /** ELM file, possibly associated with a specific application or use-case. */
        ELM("elm", TypeDataHolder.OTHERS),

        /** Control Panel file, used by Windows to access Control Panel items. */
        CPL("cpl", TypeDataHolder.OTHERS),

        /** VRG file, potentially associated with 3D or graphical applications. */
        VRG("vrg", TypeDataHolder.TEXT),

        /** Lock file, used to prevent simultaneous access to a resource. */
        LCK("lck", TypeDataHolder.OTHERS),

        /** FireFox/Thunderbird extension file, used for add-ons. */
        XPT("xpt", TypeDataHolder.OTHERS),

        /** Read-only memory file, often used in emulator software. */
        ROM("rom", TypeDataHolder.OTHERS),

        /** Valve Data Format file, possibly used in gaming by Valve Corporation. */
        VDF("vdf", TypeDataHolder.OTHERS),

        /** DirectShow file, related to multimedia processing in Windows. */
        AX("ax", TypeDataHolder.OTHERS),

        /** ISS file, possibly associated with installation or setup scripts. */
        ISS("iss", TypeDataHolder.TEXT),

        /** Managed Object Format, used in system management. */
        MOF("mof", TypeDataHolder.TEXT),

        /** Source Code Control file, used in version control environments. */
        SCC("scc", TypeDataHolder.OTHERS),

        /** Project file, often related to design or multimedia applications. */
        ZDCT("zdct", TypeDataHolder.TEXT),

        /** Precompiled Setup Information file for Windows installations. */
        PNF("pnf", TypeDataHolder.OTHERS),

        /** Windows Theme Pack file, used for applying themes on Windows. */
        THEME("theme", TypeDataHolder.OTHERS),

        /** Diagnostic package, used for troubleshooting system issues. */
        DIAGPKG("diagpkg", TypeDataHolder.OTHERS),

        /** Microsoft Office Cover Page, related to template cover pages. */
        CVR("cvr", TypeDataHolder.OTHERS),

        /** Multilingual User Interface file, used for localization in Windows. */
        MUI("mui", TypeDataHolder.OTHERS),

        /** Download manager link, related to downloading operations. */
        DLM("dlm", TypeDataHolder.OTHERS),

        /** Windows NT script or configuration file. */
        NT("nt", TypeDataHolder.TEXT),

        /** Separator file, potentially used in data handling or processing. */
        SEP("sep", TypeDataHolder.TEXT),

        /** Lexmark printer data file, used by Lexmark printers. */
        LXA("lxa", TypeDataHolder.OTHERS),

        /** NGR file, associated with specialized software or hardware. */
        NGR("ngr", TypeDataHolder.OTHERS),

        /** Journal file, used by certain Microsoft products for note-taking. */
        JNT("jnt", TypeDataHolder.OTHERS),

        /** Template file, potentially used by journal or note-taking applications. */
        JTP("jtp", TypeDataHolder.OTHERS),

        /** System Deployment Image file, used in Windows deployment. */
        SDI("sdi", TypeDataHolder.OTHERS),

        /** Mozilla fastload file, used to improve start-up performance. */
        MFL("mfl", TypeDataHolder.OTHERS),

        /** Microsoft Help Compilation file, used in help documentations. */
        H1C("h1c", TypeDataHolder.OTHERS),

        /** Microsoft Help Index file, used in help systems. */
        H1K("h1k", TypeDataHolder.OTHERS),

        /** Microsoft ClickOnce Deployment Manifest, related to software deployment. */
        CDF_MS("cdf-ms", TypeDataHolder.OTHERS),

        /** OLE Automation Object Library, used in COM programming. */
        OLB("olb", TypeDataHolder.OTHERS),

        /** Compatibility Database, used to store application compatibility data. */
        SDB("sdb", TypeDataHolder.OTHERS),

        /** Windows MST file, related to transformations on installer packages. */
        MST("mst", TypeDataHolder.OTHERS),

        /** Microsoft Common Console Document, used in administration tools. */
        MSC("msc", TypeDataHolder.OTHERS),

        /** Type Library file, describes COM types. */
        TLB("tlb", TypeDataHolder.OTHERS),

        /** Device driver file, used for hardware abstraction. */
        DRV("drv", TypeDataHolder.OTHERS),

        /** Object Linking and Embedding file, used in Windows applications. */
        OLE("ole", TypeDataHolder.OTHERS),

        /** Software package, potentially used in various applications. */
        ZDA("zda", TypeDataHolder.OTHERS),

        /** TAT file, associated with specific applications or use-cases. */
        TAT("tat", TypeDataHolder.OTHERS),

        // 链接 (Link)

        /** Microsoft CD Audio Track shortcut, represents an audio track on a CD. */
        CDA("cda", TypeDataHolder.AUDIO),

        /** Windows shortcut file, stores a path to a file/folder. */
        LNK("lnk", TypeDataHolder.LINK),

        /** URL shortcut, typically links to a webpage. */
        URL("url", TypeDataHolder.LINK),

        /** Web location shortcut, used on macOS for hyperlinks to webpages. */
        WEBLOC("webloc", TypeDataHolder.LINK),

        /** Desktop entry file for freedesktop.org compliant desktops, typically used in Linux. */
        FREEDESKTOP_DESKTOP("desktop", TypeDataHolder.LINK),

        /** Directory entry file for freedesktop.org compliant systems, used to define directories. */
        FREEDESKTOP_DIRECTORY("directory", TypeDataHolder.LINK),

        // 未知 (Unknown)

        UNKNOWN("?")
        ;

        @JvmField
        val extension = when {
            typeName.startsWith(".") -> ""
            typeName.contains(".") -> typeName
            Regex("[\\w.\\-]+").containsMatchIn(typeName) -> typeName.split("[^\\w.\\-]".toRegex()).last(String::isNotEmpty)
            else -> ""
        }

        @JvmField
        val extensionWithDot = ".$extension"

        @JvmField
        val icon = Icon(
            text = typeData.iconData.iconName ?: typeName,
            textSize = typeData.iconData.size,
            textPadding = typeData.iconData.run { arrayOf(toEnd, toBottom, toStart, toTop) },
            rotation = typeData.iconData.degree,
            includeFontPadding = typeData.iconData.excludeFontPadding.not(),
        )

        @JvmField
        val identity: Int = typeData.identity

        fun isExecutable() = identity and IDENTITY_EXECUTABLE != 0

        fun isInstallable() = identity and IDENTITY_INSTALLABLE != 0

        fun isTextEditable() = identity and IDENTITY_TEXT_EDITABLE != 0

        fun isExternalEditable() = identity and IDENTITY_EXTERNAL_EDITABLE != 0

        fun isMediaMenu() = identity and IDENTITY_MEDIA_MENU != 0

        fun isMediaPlayable() = identity and IDENTITY_MEDIA_PLAYABLE != 0

        @Suppress("ArrayInDataClass")
        data class Icon(
            @JvmField val text: String,
            @JvmField val textSize: Number? = null,
            @JvmField val textPadding: Array<Number>? = null,
            @JvmField val rotation: Number? = null,
            @JvmField val includeFontPadding: Boolean? = null,
        )

        companion object {

            const val TYPE_NAME_PREFIX_REGEX = "regex:"

            const val IDENTITY_EXECUTABLE = 0x01
            const val IDENTITY_INSTALLABLE = 0x02
            const val IDENTITY_TEXT_EDITABLE = 0x04
            const val IDENTITY_EXTERNAL_EDITABLE = 0x08
            const val IDENTITY_MEDIA_MENU = 0x10
            const val IDENTITY_MEDIA_PLAYABLE = 0x20

            @JvmStatic
            fun determineBy(file: File): TYPE {
                val matchedTypes = entries.filter {
                    when {
                        it.extension.isEmpty() -> false
                        else -> when {
                            file.name.contains(Regex("\\b(${Regex.escape(it.extension)})$", IGNORE_CASE)) -> true
                            it.extension.startsWith(TYPE_NAME_PREFIX_REGEX) && file.name.contains(Regex("\\b(${it.extension.removePrefix(TYPE_NAME_PREFIX_REGEX)})$", IGNORE_CASE)) -> true
                            else -> false
                        }
                    }
                }
                return when (matchedTypes.size) {
                    0 -> null
                    1 -> matchedTypes.firstOrNull()
                    else -> findBestExtensionTypes(matchedTypes).let { types ->
                        when {
                            types.size < 2 -> types.firstOrNull()
                            else -> types.fold(null) { acc: Pair<TYPE, Int>?, type: TYPE ->
                                val weight = type.candidateCriteria.sumOf { if (it.criterion.test(file)) it.weight else 0 }
                                when {
                                    acc == null || weight > acc.second -> type to weight
                                    weight == acc.second -> {
                                        if (type.priority > acc.first.priority) {
                                            type to weight
                                        } else {
                                            acc // keep current best
                                        }
                                    }
                                    else -> acc // keep current best
                                }
                            }?.first
                        }
                    }
                } ?: UNKNOWN
            }

            private fun findBestExtensionTypes(list: List<TYPE>): List<TYPE> {
                val regexMatched = mutableListOf<TYPE>()

                var maxLength = 0
                val longest = mutableListOf<TYPE>()

                for (item in list) {
                    if (item.typeName.startsWith(TYPE_NAME_PREFIX_REGEX)) {
                        regexMatched.add(item)
                        continue
                    }
                    val length = item.extension.length
                    when {
                        length > maxLength -> {
                            // 找到更长的元素, 清空结果列表并添加新元素
                            maxLength = length
                            longest.clear()
                            longest.add(item)
                        }
                        length == maxLength -> {
                            // 找到相同最大长度的元素, 添加到结果列表
                            longest.add(item)
                        }
                    }
                }

                return regexMatched + longest
            }

        }

    }

    private fun File.isMpeg2TsLike(): Boolean {
        val magicByte = 0x47.toByte()
        val tsPacketSize = 188
        return runCatching {
            this@isMpeg2TsLike.inputStream().use { inputStream ->
                val buffer = ByteArray(tsPacketSize)
                when (inputStream.read(buffer)) {
                    tsPacketSize -> buffer[0] == magicByte
                    else -> false
                }
            }
        }.getOrElse { false }
    }

    private fun File.isTypeScriptLike(): Boolean {
        return checkStartsWith("function", "import", "export", "interface", "class")
    }

    private fun File.isObjectiveCLike(): Boolean {
        return checkContains("@interface", "@implementation", "#import")
    }

    private fun File.isMatlabLike(): Boolean {
        return checkStartsWith("function", "%", "end")
    }

    private fun File.isGenerateDataLike(): Boolean {
        return checkStartsWith("DATA", "INFO", "HEADER", "META", "RECORD", "BINARY")
                || checkContains("DATA_TYPE", "VERSION", "FORMAT")
    }

    private fun File.isWavefrontObjLike(): Boolean {
        return checkContains("Wavefront", "mtllib", "3ds max")
                || checkRegex(Regex("^v\\s+-?\\d"))
    }

    private fun File.isVcdDataLike() = runCatching {
        val riffMagicNumber = byteArrayOf(0x52, 0x49, 0x46, 0x46)  // "RIFF"
        this@isVcdDataLike.inputStream().use { inputStream ->
            val buffer = ByteArray(4)
            inputStream.read(buffer)
            buffer.contentEquals(riffMagicNumber)
        }
    }.getOrElse { false }

    private fun File.isBinArchiveLike() = runCatching {
        val zipMagicNumber = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte())
        val gzipMagicNumber = byteArrayOf(0x1F.toByte(), 0x8B.toByte())
        this@isBinArchiveLike.inputStream().use { inputStream ->
            val header = ByteArray(4)
            if (inputStream.read(header) == header.size) {
                return@runCatching header.sliceArray(0..3).contentEquals(zipMagicNumber) ||
                        header.sliceArray(0..1).contentEquals(gzipMagicNumber)
            }
        }
        // If no known compressed header is found, assume it's not a compressed file
        false
    }.getOrElse { false }

    private fun File.isBinDiscImageLike() = runCatching {
        this@isBinDiscImageLike.inputStream().use { inputStream ->
            val header = ByteArray(2048) // Read first sector
            if (inputStream.read(header) != header.size) return false

            // Check for very basic ISO 9660 primary volume descriptor
            val pvdDescriptor = "CD001".toByteArray()
            header.sliceArray(0x800 until 0x800 + pvdDescriptor.size).contentEquals(pvdDescriptor)
        }
    }.getOrElse { false }

    // Determines if a CUE file describes audio tracks
    private fun File.isAudioCueSheetLike() = runCatching {
        this@isAudioCueSheetLike.useLines { lines ->
            lines.any { line ->
                line.contains(Regex("""(?i)FILE .* (\.wav|\.mp3|\.flac)""")) || // Checks for common audio extensions
                        line.contains(Regex("""(?i)TRACK \d+ AUDIO""")) // Checks for audio track indicator
            }
        }
    }.getOrElse { false }

    // Determines if a CUE file describes a disk image
    private fun File.isDiskImageCueSheetLike() = runCatching {
        this@isDiskImageCueSheetLike.useLines { lines ->
            lines.any { line ->
                line.contains(Regex("""(?i)FILE .* (\.bin|\.iso)""")) || // Checks for common binary extensions
                        line.contains(Regex("""(?i)TRACK \d+ MODE\d""")) // Checks for data track indicator
            }
        }
    }.getOrElse { false }

    // Determines if an M3U file is likely an audio playlist
    private fun File.isAudioM3ULike() = runCatching {
        this@isAudioM3ULike.useLines { lines ->
            lines.any { line ->
                line.contains(Regex("""(?i)\.(mp3|wav|aac|flac|ape|m4a|ogg)$""")) // Checks for common audio file extensions
            }
        }
    }.getOrElse { false }

    // Determines if an M3U file is likely a video playlist
    private fun File.isVideoM3ULike() = runCatching {
        this@isVideoM3ULike.useLines { lines ->
            lines.any { line ->
                line.contains(Regex("""(?i)\.(mp4|avi|mkv|mov|wmv|ts)$""")) // Checks for common video file extensions
            }
        }
    }.getOrElse { false }

    private fun File.isProguardConfigLike() = checkContains(
        "-injars", "-outjars", "-libraryjars", "-printmapping", "-overloadaggressively",
        maxLinesToCheck = 200,
    )

    private fun File.isQmakeProjectLike() = checkContains(
        "TEMPLATE", "TARGET", "CONFIG", "SOURCES", "HEADERS", "RESOURCES", "INCLUDEPATH", "LIBS", "DEFINES",
        maxLinesToCheck = 200,
    )

    private fun File.isMarkdownMDLike() = runCatching {
        this@isMarkdownMDLike.useLines { lines ->
            lines.any { line ->
                return@any line.startsWith("#")
                        || line.startsWith("- ") || line.startsWith("* ")
                        || line.contains("```")
            }
        }
    }.getOrElse { false }

    private fun File.isSegaMDLike() = runCatching {
        this@isSegaMDLike.inputStream().use { inputStream ->
            val header = ByteArray(512) // 假设 SEGA 游戏文件有特定的头部
            if (inputStream.read(header) != header.size) return false

            // 这里你需要根据 SEGA 文件的具体特征来调整条件
            // 例如: 找到特定的标识符或者模式
            val segaIdentifier = byteArrayOf(0x53, 0x45, 0x47, 0x41) // 可能的 "SEGA" 标记
            header.sliceArray(segaIdentifier.indices).contentEquals(segaIdentifier)
        }
    }.getOrElse { false }

    // Function to check if a file is likely a macro based on scripting patterns found in samples
    private fun File.isMacroFileLike(): Boolean {
        return runCatching {
            this.useLines { lines ->
                lines.any { line ->
                    // Check for commented lines, a typical pattern in scripting languages
                    line.matches(Regex("""^\s*'""")) ||
                            // Check for the usage of "Set", indicating VBScript-like style
                            line.contains(Regex("""(?i)\bSet\b\s+""")) ||
                            // Check for "Include" structure
                            line.contains(Regex("""(?i)\bInclude\b\s*\(""")) ||
                            // Check for common scripting structures like creating objects or defining methods
                            line.contains(Regex("""(?i)\bCreateObject\b""")) ||
                            line.contains(Regex("""(?i)Function\b""")) ||
                            line.contains(Regex("""(?i)\bFor\b""")) ||
                            line.contains(Regex("""(?i)\bIf\b"""))
                }
            }
        }.getOrElse { false }
    }

    // Function to determine if a file is likely a Monkey's Audio (.ape) file
    private fun File.isMonkeyAudioLike(): Boolean {
        return runCatching {
            this.inputStream().use { inputStream ->
                val header = ByteArray(4)
                // Attempt to read the first 4 bytes of the file
                if (inputStream.read(header) == header.size) {
                    // Compare the read bytes to the expected 'MAC ' signature
                    header.contentEquals(byteArrayOf(0x4D, 0x41, 0x43, 0x20)) // 'MAC '
                } else false
            }
        }.getOrElse { false }
    }

    private fun File.isEbuStlLike(): Boolean {
        // EBU - Subtitling data exchange format
        return runCatching {
            this.inputStream().use { inputStream ->
                val header = ByteArray(6)
                // Attempt to read the first 6 bytes of the file
                if (inputStream.read(header) == header.size) {
                    // GSI block description
                    // Code Page Number (CPN), The number of the code page used in the GSI block.
                    // For international exchanges, one of the five code pages supported by MS/PC-DOS, version 3.3 must be used in the GSI block.
                    // These code pages are listed below and reproduced in Appendix 1.
                    // Other code pages may be used within a given national environment (e.g.: Greek code page 928).
                    // -----------------------------------------------------------
                    // Code Page Number (CPN) | Character set | Hex representation
                    // -----------------------------------------------------------
                    //          437           | United States |    34h 33h 37h
                    //          850           | Multilingual  |    38h 35h 30h
                    //          860           |   Portugal    |    38h 36h 30h
                    //          863           | Canada-French |    38h 36h 33h
                    //          865           |    Nordic     |    38h 36h 35h
                    header.sliceArray(0 until 3).let {
                        return@let it.contentEquals("437".toByteArray())
                                || it.contentEquals("850".toByteArray())
                                || it.contentEquals("860".toByteArray())
                                || it.contentEquals("863".toByteArray())
                                || it.contentEquals("865".toByteArray())
                    } || header.sliceArray(3 until 6).let {
                        return@let it.contentEquals("STL".toByteArray())
                    }
                } else false
            }
        }.getOrElse { false }
    }

    private fun File.isModelStlLike(): Boolean {
        return runCatching {
            this.inputStream().use { inputStream ->
                val header = ByteArray(80) // STL binary files start with an 80-byte header
                if (inputStream.read(header) != header.size) return@use false

                // Check for ASCII STL by reading the file beginning for "solid" keyword
                val bufferedReader = this.bufferedReader(Charsets.US_ASCII)
                bufferedReader.mark(100) // Mark the initial position
                val firstLine = bufferedReader.readLine()
                bufferedReader.reset() // Reset back to the initial position
                if (firstLine.trimStart().startsWith("solid")) return@use true

                // For binary STL, continue reading for triangle count
                val triangleCountBytes = ByteArray(4)
                inputStream.use {
                    it.skip(80L) // Skip the header
                    it.read(triangleCountBytes)
                }
                val triangles = triangleCountBytes.foldIndexed(0) { index, acc, byte ->
                    acc or ((byte.toInt() and 0xFF) shl (8 * index))
                }

                // Calculate expected size based on the triangle count
                val expectedBinarySize = 80 + 4 + triangles * 50
                return@use this.length() == expectedBinarySize.toLong()
            }
        }.getOrElse { false }
    }

    private fun File.isModel3dsLike(): Boolean {
        return runCatching {
            this.inputStream().use { inputStream ->
                val header = ByteArray(2)
                if (inputStream.read(header) == header.size) {
                    val identifier = "MM".toByteArray()
                    header.sliceArray(identifier.indices).contentEquals(identifier)
                } else false
            }
        }.getOrElse { false }
    }

    private fun File.isNintendo3dsLike(): Boolean {
        return runCatching {
            this.inputStream().use { inputStream ->
                val magicBytes = ByteArray(4)
                if (inputStream.read(magicBytes) == magicBytes.size) {
                    // An example magic number that the .3ds file might have, might need adjustment based on actual known values
                    val expectedIdentifier = byteArrayOf(0x3D, 0x53, 0x2E, 0x43) // Hypothetical identifier for .3ds (project specific)
                    magicBytes.contentEquals(expectedIdentifier)
                } else false
            }
        }.getOrElse { false }
    }

    private fun File.checkStartsWith(vararg keywords: String, maxLinesToCheck: Int = 500): Boolean {
        return checkRegex(*keywords.map { Regex("^\\s*${Regex.escape(it)}") }.toTypedArray(), maxLinesToCheck = maxLinesToCheck)
    }

    private fun File.checkRegex(vararg keywords: Regex, maxLinesToCheck: Int = 500): Boolean {
        return check(*keywords, maxLinesToCheck = maxLinesToCheck) { line, keyword -> line.contains(Regex(keyword.pattern, IGNORE_CASE)) }
    }

    private fun File.checkContains(vararg keywords: String, maxLinesToCheck: Int = 500): Boolean {
        return check(*keywords, maxLinesToCheck = maxLinesToCheck) { line, keyword -> line.contains(keyword, ignoreCase = true) }
    }

    private fun <T> File.check(vararg keywords: T, maxLinesToCheck: Int = 500, lineCheck: (String, T) -> Boolean): Boolean {
        return runCatching {
            this@check.bufferedReader().useLines { lines ->
                lines.take(maxLinesToCheck).any { line ->
                    val trimmedLine = line.trim()
                    keywords.any { keyword -> lineCheck(trimmedLine, keyword) }
                }
            }
        }.getOrElse { false }
    }

    fun File.withSameNameExtension(extensionName: String): Boolean {
        // Ensure the file has a valid parent directory
        val parentDir = parentFile ?: return false
        // Get the base name without extension
        val baseName = nameWithoutExtension

        // List files in the directory and check for same base name with specified extension
        return parentDir.listFiles()?.any { file ->
            file.isFile && file.nameWithoutExtension == baseName && file.extension == extensionName
        } ?: false
    }

    private fun File.isSizeGreaterThan(size: Long, unit: SizeUnit) = length() > size * unit.multiplier

    private fun File.isSizeLessThan(size: Long, unit: SizeUnit) = length() < size * unit.multiplier

    @Suppress("unused")
    enum class SizeUnit(val multiplier: Long) {
        BYTES(1),
        B(1),
        KB(1024),
        MB(1024 * 1024),
        GB(1024 * 1024 * 1024),
    }

    data class IconData(
        val iconName: String? = null,
        val size: Number = 23,
        val toStart: Number = 0,
        val toTop: Number = 0,
        val toEnd: Number = 0,
        val toBottom: Number = 0,
        val degree: Number = 0,
        val excludeFontPadding: Boolean = false,
    )

    data class TypeData(val iconData: IconData, @JvmField val identity: Int = 0x0)

    /**
     * Represents a criterion used to evaluate files with a specific weight.
     *
     * zh-CN: 候选匹配标准, 用于评估权重评分.
     *
     * @property criterion zh-CN: 匹配标准.
     * @property weight zh-CN: 权重.
     */
    data class CandidateCriterion(val criterion: Predicate<File>, val weight: Int)

}
