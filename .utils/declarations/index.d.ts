type Page = import('puppeteer').Page;
type Frame = import('puppeteer').Frame;
type ElementHandle<T extends Node> = import('puppeteer').ElementHandle<T>;

type CommitsData = import('@octokit/types').Endpoints['GET /repos/{owner}/{repo}/commits']['response']['data'];
type ReleasesData = import('@octokit/types').Endpoints['GET /repos/{owner}/{repo}/releases']['response']['data']
type PullsData = import('@octokit/types').Endpoints['GET /repos/{owner}/{repo}/pulls']['response']['data'];
type PullCommitsData = import('@octokit/types').Endpoints['GET /repos/{owner}/{repo}/pulls/{pull_number}/commits']['response']['data'];

type AndroidStudioStableArchiveItemKind = 'exe' | 'zip' | 'tar' | 'other';

type FindTargetRowsFilter = string | RegExp | ((s: string) => boolean);

type TableDataStructureItemName = string;
type TableDataStructureItem = RegExp | ((s: string) => boolean | string);
type TableDataStructureItemForPageEvaluate = string | RegExp | ((s: string) => boolean | string);

interface AndroidStudioArchiveItem {
    title: string;
    date: string;
    version: string | null;
    links: Array<{
        text: string;
        href: string;
    }>;
    checksums: { [filename: string]: string };
}

interface AndroidStudioStableArchiveItem {
    platform: string;
    filename: string;
    size: string;
    sha256: string;
    url: string | null;
    kind: AndroidStudioStableArchiveItemKind;
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

interface AnchoredBlockUpdateOption {
    type: 'map' | 'list' | 'custom';
    anchorTag: string;
    mapName?: string;
    listName?: string;
    lines: string[];
    linesIndent?: number;
    updatedLabel?: string;
    replacer?: (srcInBlock: string, options: { toUpdatedStamp?: (date?: Date) => string }) => { newBlock: string, changed: boolean };
}
