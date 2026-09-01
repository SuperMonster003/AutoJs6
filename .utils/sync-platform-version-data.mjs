import * as fsp from 'node:fs/promises';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseProperties } from './utils/properties.mjs';

const UTILS_DIR = path.dirname(fileURLToPath(import.meta.url));
const AUTOJS6_DIR = path.resolve(UTILS_DIR, '..');
const DATA_RELATIVE_DIR = path.join('src', 'main', 'resources', 'org', 'autojs', 'build', 'platform', 'data');

export const PLATFORM_VERSIONS_DIR_ENV = 'AUTOJS6_GRADLE_PLATFORM_VERSIONS_DIR';
export const DEFAULT_PLATFORM_VERSIONS_DIR = path.resolve(AUTOJS6_DIR, '..', 'AutoJs6-Gradle-Platform-Versions');

function requireString(value, field) {
    if (typeof value !== 'string' || !value.trim()) {
        throw new Error(`Invalid Android Studio metadata field: ${field}`);
    }
    return value.trim();
}

function requireDownload(value, field) {
    if (!value || typeof value !== 'object') throw new Error(`Invalid Android Studio download: ${field}`);
    const fileName = requireString(value.fileName, `${field}.fileName`);
    const url = requireString(value.url, `${field}.url`);
    if (!Number.isSafeInteger(value.sizeBytes) || value.sizeBytes <= 0) {
        throw new Error(`Invalid Android Studio download size: ${field}.sizeBytes`);
    }
    const parsedUrl = new URL(url);
    if (parsedUrl.protocol !== 'https:') throw new Error(`Android Studio download URL must use HTTPS: ${url}`);
    const urlFileName = decodeURIComponent(parsedUrl.pathname.split('/').filter(Boolean).at(-1) || '');
    if (urlFileName !== fileName) {
        throw new Error(`Android Studio download file name does not match its URL: ${fileName} vs ${urlFileName}`);
    }
    return { fileName, url, sizeBytes: value.sizeBytes };
}

function codenameCodeFor(version, codenameVersions) {
    return [ ...codenameVersions ]
        .filter(([ prefix ]) => version === prefix || version.startsWith(`${prefix}.`))
        .sort(([ left ], [ right ]) => right.length - left.length)
        .at(0)?.[1];
}

export function validateLatestStableMetadata(metadata, datasets) {
    if (!metadata || typeof metadata !== 'object' || metadata.schemaVersion !== 1) {
        throw new Error('Unsupported Android Studio metadata schema; expected schemaVersion 1');
    }
    const name = requireString(metadata.name, 'name');
    const version = requireString(metadata.version, 'version');
    const build = requireString(metadata.build, 'build');
    const releaseDate = requireString(metadata.releaseDate, 'releaseDate');
    if (!/^\d{4}(?:\.\d+){1,3}$/.test(version)) {
        throw new Error(`Invalid Android Studio stable version: ${version}`);
    }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(releaseDate)
        || Number.isNaN(new Date(`${releaseDate}T00:00:00Z`).getTime())) {
        throw new Error(`Invalid Android Studio release date: ${releaseDate}`);
    }

    const buildKey = build.replace(/[^\d.]/g, '');
    const mappedVersion = datasets.buildVersions.get(buildKey);
    if (mappedVersion !== version) {
        throw new Error(`Android Studio stable metadata is inconsistent with the build map: ${buildKey} -> ${mappedVersion ?? '<missing>'}, expected ${version}`);
    }

    const codenameCode = codenameCodeFor(version, datasets.codenameVersions);
    const codename = codenameCode && datasets.codenames.get(codenameCode);
    if (!codename || !name.toLowerCase().startsWith(`android studio ${codename}`.toLowerCase())) {
        throw new Error(`Android Studio stable metadata is inconsistent with the codename maps: ${version} -> ${codename ?? '<missing>'}`);
    }

    return {
        ...metadata,
        name,
        version,
        build,
        releaseDate,
        downloads: {
            windowsExe: requireDownload(metadata.downloads?.windowsExe, 'downloads.windowsExe'),
            windowsZip: requireDownload(metadata.downloads?.windowsZip, 'downloads.windowsZip'),
            linuxTar: requireDownload(metadata.downloads?.linuxTar, 'downloads.linuxTar'),
        },
    };
}

export function buildUpdatedCommonJson(original, metadata) {
    if (!original || typeof original !== 'object' || Array.isArray(original)) {
        throw new Error('Invalid AutoJs6 .readme/common.json root value');
    }
    const toGiB = (bytes) => `${(bytes / 1024 ** 3).toFixed(2)} GiB`;
    const { windowsExe, windowsZip, linuxTar } = metadata.downloads;
    return {
        ...original,
        android_studio_latest_recommended_version_name: metadata.name,
        var_date_android_studio_latest_recommended_version_name: metadata.releaseDate.replaceAll('-', '/'),
        android_studio_latest_recommended_file_name_of_exe: windowsExe.fileName,
        android_studio_latest_recommended_download_address_of_exe: windowsExe.url,
        android_studio_latest_recommended_file_size_of_exe: toGiB(windowsExe.sizeBytes),
        android_studio_latest_recommended_file_name_of_zip: windowsZip.fileName,
        android_studio_latest_recommended_download_address_of_zip: windowsZip.url,
        android_studio_latest_recommended_file_size_of_zip: toGiB(windowsZip.sizeBytes),
        android_studio_latest_recommended_file_name_of_tar: linuxTar.fileName,
        android_studio_latest_recommended_download_address_of_tar: linuxTar.url,
        android_studio_latest_recommended_file_size_of_tar: toGiB(linuxTar.sizeBytes),
    };
}

export function resolvePlatformVersionsDir(env = process.env) {
    const configured = env[PLATFORM_VERSIONS_DIR_ENV]?.trim();
    return configured ? path.resolve(configured) : DEFAULT_PLATFORM_VERSIONS_DIR;
}

export async function loadLatestStableMetadata(platformVersionsDir) {
    const dataDir = path.join(platformVersionsDir, DATA_RELATIVE_DIR);
    const paths = {
        metadata: path.join(dataDir, 'android-studio-latest-stable.json'),
        buildVersions: path.join(dataDir, 'android-studio-build-version.properties'),
        codenameVersions: path.join(dataDir, 'android-studio-codename-version.properties'),
        codenames: path.join(dataDir, 'android-studio-codename.properties'),
    };
    let contents;
    try {
        contents = await Promise.all([
            fsp.readFile(paths.metadata, 'utf8'),
            fsp.readFile(paths.buildVersions, 'utf8'),
            fsp.readFile(paths.codenameVersions, 'utf8'),
            fsp.readFile(paths.codenames, 'utf8'),
        ]);
    } catch (error) {
        throw new Error(
            `Platform-version data is unavailable in ${platformVersionsDir}. Run that repository's run-scrapers.bat first, or set ${PLATFORM_VERSIONS_DIR_ENV}.`,
            { cause: error },
        );
    }
    const [ metadataText, buildVersionsText, codenameVersionsText, codenamesText ] = contents;
    return validateLatestStableMetadata(JSON.parse(metadataText), {
        buildVersions: parseProperties(buildVersionsText),
        codenameVersions: parseProperties(codenameVersionsText),
        codenames: parseProperties(codenamesText),
    });
}

export async function main() {
    const platformVersionsDir = resolvePlatformVersionsDir();
    const metadata = await loadLatestStableMetadata(platformVersionsDir);
    const commonJsonPath = path.join(AUTOJS6_DIR, '.readme', 'common.json');
    const originalText = await fsp.readFile(commonJsonPath, 'utf8');
    const original = JSON.parse(originalText);
    const updated = buildUpdatedCommonJson(original, metadata);
    const updatedText = JSON.stringify(updated, null, 2);

    if (updatedText === originalText) {
        // console.log(`[common.json] No update needed (Android Studio ${metadata.version} from ${path.basename(platformVersionsDir)})`);
        return false;
    }
    await fsp.writeFile(commonJsonPath, updatedText, 'utf8');
    console.log(`[common.json] Updated (Android Studio ${metadata.version} from ${path.basename(platformVersionsDir)})`);
    if (original.android_studio_latest_recommended_version_name !== metadata.name) {
        console.log(`  - ${original.android_studio_latest_recommended_version_name}`);
        console.log(`  + ${metadata.name}`);
    }
    return true;
}

const ownPath = path.resolve(fileURLToPath(import.meta.url));
const entryPath = process.argv[1] && path.resolve(process.argv[1]);
if (entryPath && (process.platform === 'win32'
    ? ownPath.toLowerCase() === entryPath.toLowerCase()
    : ownPath === entryPath)) {
    await main().catch((error) => {
        console.error(error);
        process.exitCode = 1;
    });
}
