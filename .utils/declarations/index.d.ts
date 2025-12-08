type Cheerio<T> = import('cheerio').Cheerio<T>;
type DomElement = import('domhandler').Element;

type Page = import('puppeteer').Page;
type Frame = import('puppeteer').Frame;
type ElementHandle<T extends Node> = import('puppeteer').ElementHandle<T>;

type CommitsData = import('@octokit/types').Endpoints['GET /repos/{owner}/{repo}/commits']['response']['data'];
type ReleasesData = import('@octokit/types').Endpoints['GET /repos/{owner}/{repo}/releases']['response']['data']
type PullsData = import('@octokit/types').Endpoints['GET /repos/{owner}/{repo}/pulls']['response']['data'];
type PullCommitsData = import('@octokit/types').Endpoints['GET /repos/{owner}/{repo}/pulls/{pull_number}/commits']['response']['data'];

type FindTargetRowsFilter = string | RegExp | ((s: string) => boolean);

type TableDataStructureItemName = string;
type TableDataStructureItem = RegExp | ((s: string) => boolean | string);
type TableDataStructureItemForPageEvaluate = string | RegExp | ((s: string) => boolean | string);

interface AndroidReleaseWiki {
    RELEASE_NAME: string;
    INTERNAL_CODENAME: string;
    PLATFORM_VERSION: string;
    API_LEVEL: number;
    RELEASE_DATE: string;
}

interface AndroidReleaseCsv extends AndroidReleaseWiki {
    VERSION_CODE: string;
}

interface AndroidStudioRelease {
    content: {
        item: AndroidStudioReleaseItem[];
    };
    version: number;
}

interface AndroidStudioReleaseItem {
    /** @example 'September 29, 2025' */
    date: string;
    /** @example '251.27812.49' */
    platformBuild: string;
    download: AndroidStudioReleaseDownloadItem[];
    /** @example 'AI-251.27812.49.2514.14171003' */
    build: string;
    /** @example '2025.1.5' */
    platformVersion: string;
    /** @example 'Android Studio Narwhal 4 Feature Drop | 2025.1.4 RC 2' */
    name: string;
    channel: 'Preview' | 'Canary' | 'Beta' | 'RC' | 'Release' | 'Patch';
    /** @example '2025.1.4.7' */
    version: string;
}

interface AndroidStudioReleaseDownloadItem {
    /** @example '1.4 GB' */
    size: string;
    /** @example 'https://redirector.gvt1.com/edgedl/android/studio/ide-zips/2025.1.4.7/android-studio-2025.1.4.7-windows.zip' */
    link: string;
    /** @example '91b48f1561cda0387e7499fa7e425908aa5f1235ce36aec1383fa7091f5c242a' */
    checksum: string;
}

interface FindTargetRowsOptionsBase {
    /** @default 'table' */
    tableSelector?: 'table' | string;
    tableFilter?: { [selector: string]: FindTargetRowsFilter | FindTargetRowsFilter[] };
    /** @default 'tbody tr' */
    tableRowSelector?: 'tbody tr' | string;
    /** @default 'td' */
    tableDataSelector?: 'td' | string;
    /** @default [] */
    tableDataStructure?: Array<{ [dataItemName: TableDataStructureItemName]: any } | TableDataStructureItemName>;
}

interface FindTargetRowsOptions extends FindTargetRowsOptionsBase {
    /** @default [] */
    tableDataStructure?: Array<{ [dataItemName: TableDataStructureItemName]: TableDataStructureItem } | TableDataStructureItemName>;

    /**
     * Configuration for "expand" actions needed before searching the table.
     * Example: { toggleSelector: 'a.exw-control' } will click Devsite's collapsible controls.
     * zh-CN:
     * 在查找表格前需要进行的 "展开" 动作配置.
     * 示例: { toggleSelector: 'a.exw-control' } 将点击 Devsite 的可折叠控件.
     */
    expandBeforeFinding?: ExpandBeforeFindingOptions | ExpandBeforeFindingOptions[];
}

interface ExpandBeforeFindingOptions {
    /**
     * Optional container selector to limit the scope of operation.
     * zh-CN: 限定作用范围容器选择器, 可选.
     */
    containerSelector?: string;
    /**
     * Selector for the "expand/collapse" button or toggle that needs to be clicked (required).
     * zh-CN: 需要点击的 "展开/折叠" 按钮或开关的选择器 (必填).
     */
    toggleSelector: string;
    /**
     * Attribute name to determine if expanded, defaults to 'aria-expanded'.
     * zh-CN: 判定已展开的属性名, 默认 'aria-expanded'.
     *
     * @default 'aria-expanded'
     */
    expandedAttr?: string;
    /**
     * Attribute value to determine if expanded, defaults to 'true'.
     * zh-CN: 判定已展开的属性值, 默认 'true'.
     *
     * @default 'true'
     */
    expandedValue?: string;
    /**
     * Maximum number of targets to click, no limit by default.
     * zh-CN: 最多点击的目标数量, 默认无限制.
     *
     * @default Infinity
     */
    maxClicks?: number;
}

interface FindTargetRowsOptionsForPageEvaluate extends FindTargetRowsOptionsBase {
    /** @default [] */
    tableDataStructure?: Array<{ [dataItemName: TableDataStructureItemName]: TableDataStructureItemForPageEvaluate } | TableDataStructureItemName>;
}

interface PuppeteerOptions {
    url: string;
    /** @default 120000 */
    pageGoToTimeout?: 120000 | number;
    /** @default 30000 */
    findTargetRowsTimeout?: 30000 | number;
}

interface KspRelease {
    version: string;
    name: string;
    publishedAt: string;
}

interface UserProfile {
    login: string;
    name: string | null;
}

interface StatisticsEntry {
    login: string;
    name: string | null;
    htmlUrl: string;
    totalCommitsInMergedPRs: number;
    latestCommitAt: string | null;
}

interface DebugRecord {
    sha: string;
    belongs: boolean;
    author_login: string | null;
    committer_login: string | null;
    author_name: string | null;
    author_email: string | null;
    time: string | null;
}

interface GradleRelease {
    versionName: string;
    releaseDate: string;
    link: {
        bin: string;
        all: string;
        checksums: string;
    }
}

interface GradleReleaseConfig {
    majorVersionLimit?: number | string | null;
    format?: 'bin' | 'all';
}

interface ScriptItem {
    name: string;
    abs: string;
}

interface GradleMapUpdateOptions extends GradleDataUpdateOptions, GradleMapRwOptions {
    /* Empty body. */
}

interface GradleListUpdateOptions extends GradleDataUpdateOptions, GradleListRwOptions {
    /* Empty body. */
}

interface GradleLinesUpdateOptions extends GradleDataUpdateOptions {
    /* Empty body. */
}

interface GradleDataUpdateOptions extends GradleDataRwOptions {
    label?: string
}

interface GradleMapRwOptions extends GradleDataRwOptions, MapSortable {
    /* Empty body. */
}

interface GradleListRwOptions extends GradleDataRwOptions, ListSortable {
    /* Empty body. */
}

interface GradleDataRwOptions {
    encoding?: BufferEncoding;
}

interface MapSortable {
    sort?: `${'key' | 'value'}.${'ascending' | 'descending'}` | `${'key' | 'value'}.${'ascending' | 'descending'}.as.${'string' | 'version' | 'number'}`;
}

interface ListSortable {
    sort?: `${'ascending' | 'descending'}` | `${'ascending' | 'descending'}.as.${'string' | 'version' | 'number'}`;
}
