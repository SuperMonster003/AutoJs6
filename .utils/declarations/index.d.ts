type Cheerio<T> = import('cheerio').Cheerio<T>;
type DomElement = import('domhandler').Element;

type CommitsData = import('@octokit/types').Endpoints['GET /repos/{owner}/{repo}/commits']['response']['data'];
type ReleasesData = import('@octokit/types').Endpoints['GET /repos/{owner}/{repo}/releases']['response']['data']
type PullsData = import('@octokit/types').Endpoints['GET /repos/{owner}/{repo}/pulls']['response']['data'];
type PullCommitsData = import('@octokit/types').Endpoints['GET /repos/{owner}/{repo}/pulls/{pull_number}/commits']['response']['data'];

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
    hasCreditedPRs: boolean;
}

interface CreditedPullRequest {
    number: number;
    login: string;
    commitCount: number;
    latestCommitAt: string;
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

interface GradleMapRwOptions extends GradleDataRwOptions, MapSortable {
    /* Empty body. */
}

interface GradleDataRwOptions {
    encoding?: BufferEncoding;
}

interface MapSortable {
    sort?: `${'key' | 'value'}.${'ascending' | 'descending'}` | `${'key' | 'value'}.${'ascending' | 'descending'}.as.${'string' | 'version' | 'number'}`;
}
