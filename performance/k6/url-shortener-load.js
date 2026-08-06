import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Counter } from "k6/metrics";

// ---------------------------------------------------------------------------
// Configuration (all overridable via environment variables, see README.md)
// ---------------------------------------------------------------------------

const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/$/, "");

const STAGED = (__ENV.STAGED || "false").toLowerCase() === "true";
const STAGE_DURATION = __ENV.STAGE_DURATION || "1m";

const REDIRECT_VUS = parseInt(__ENV.REDIRECT_VUS || "10", 10);
const REDIRECT_DURATION = __ENV.REDIRECT_DURATION || __ENV.DURATION || "30s";
const REDIRECT_SLEEP_SECONDS = parseFloat(__ENV.REDIRECT_SLEEP_SECONDS || "0.2");

// Creation and analytics traffic default to a small percentage of the peak
// redirect VU count, so they stay a background workload rather than
// competing with the redirect-heavy traffic the test is meant to measure.
const PEAK_REDIRECT_VUS = STAGED ? 1000 : REDIRECT_VUS;

const CREATE_VUS = parseInt(
    __ENV.CREATE_VUS || String(Math.max(1, Math.round(PEAK_REDIRECT_VUS * 0.02))),
    10
);
const CREATE_SLEEP_SECONDS = parseFloat(__ENV.CREATE_SLEEP_SECONDS || "1");

const ANALYTICS_VUS = parseInt(
    __ENV.ANALYTICS_VUS || String(Math.max(1, Math.round(PEAK_REDIRECT_VUS * 0.01))),
    10
);
const ANALYTICS_SLEEP_SECONDS = parseFloat(__ENV.ANALYTICS_SLEEP_SECONDS || "2");

const CHROME_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

// ---------------------------------------------------------------------------
// Custom metrics
//
// http_reqs, http_req_failed and the overall http_req_duration percentiles
// (p50/p90/p95/p99) are provided by k6 automatically and read from the
// standard end-of-run summary. These three Trend metrics break request
// latency down per workload, as required.
// ---------------------------------------------------------------------------

const redirectDuration = new Trend("redirect_duration", true);
const creationDuration = new Trend("creation_duration", true);
const analyticsDuration = new Trend("analytics_duration", true);

const redirectChecksPassed = new Counter("redirect_checks_passed");
const redirectChecksFailed = new Counter("redirect_checks_failed");

// ---------------------------------------------------------------------------
// Scenarios
// ---------------------------------------------------------------------------

function parseDurationToSeconds(durationString) {
    const match = /^(?:(\d+)m)?(?:(\d+(?:\.\d+)?)s)?$/.exec(durationString);
    if (!match) {
        return 60;
    }
    const minutes = match[1] ? parseInt(match[1], 10) : 0;
    const seconds = match[2] ? parseFloat(match[2]) : 0;
    return minutes * 60 + seconds;
}

const redirectScenario = STAGED
    ? {
        executor: "ramping-vus",
        startVUs: 0,
        gracefulRampDown: "15s",
        stages: [
            { duration: STAGE_DURATION, target: 50 },
            { duration: STAGE_DURATION, target: 100 },
            { duration: STAGE_DURATION, target: 250 },
            { duration: STAGE_DURATION, target: 500 },
            { duration: STAGE_DURATION, target: 1000 },
        ],
        exec: "redirectWorkload",
    }
    : {
        executor: "constant-vus",
        vus: REDIRECT_VUS,
        duration: REDIRECT_DURATION,
        exec: "redirectWorkload",
    };

// Creation/analytics run as background scenarios for the full test window so
// they stay active throughout every redirect stage.
const backgroundDuration = STAGED
    ? `${parseDurationToSeconds(STAGE_DURATION) * 5}s`
    : (__ENV.CREATE_DURATION || __ENV.DURATION || "30s");

const analyticsBackgroundDuration = STAGED
    ? `${parseDurationToSeconds(STAGE_DURATION) * 5}s`
    : (__ENV.ANALYTICS_DURATION || __ENV.DURATION || "30s");

export const options = {
    scenarios: {
        redirects: redirectScenario,
        creations: {
            executor: "constant-vus",
            vus: CREATE_VUS,
            duration: backgroundDuration,
            exec: "createWorkload",
        },
        analyticsLookup: {
            executor: "constant-vus",
            vus: ANALYTICS_VUS,
            duration: analyticsBackgroundDuration,
            exec: "analyticsWorkload",
        },
    },
    thresholds: {
        // Provisional thresholds. See README.md limitations section: these
        // are starting points to iterate on, not validated SLAs.
        http_req_failed: ["rate<0.01"],
        redirect_duration: ["p(95)<500"],
        creation_duration: ["p(95)<1000"],
    },
    summaryTrendStats: ["avg", "min", "med", "p(50)", "p(90)", "p(95)", "p(99)", "max"],
    discardResponseBodies: true,
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function uniqueOriginalUrl(prefix) {
    const rand = Math.random().toString(36).slice(2, 10);
    return `https://example.com/${prefix}/${__VU}-${__ITER}-${Date.now()}-${rand}`;
}

function findHeader(res, headerName) {
    const target = headerName.toLowerCase();
    for (const key in res.headers) {
        if (key.toLowerCase() === target) {
            return res.headers[key];
        }
    }
    return undefined;
}

function jsonHeaders() {
    return { headers: { "Content-Type": "application/json" } };
}

// ---------------------------------------------------------------------------
// Setup: create one short URL and warm the Redis cache for it. All VUs in
// the redirect and analytics scenarios reuse this single short code, which
// deliberately drives a hot-key cache-hit workload against Redis.
// ---------------------------------------------------------------------------

export function setup() {
    const createRes = http.post(
        `${BASE_URL}/api/v1/urls`,
        JSON.stringify({ originalUrl: uniqueOriginalUrl("setup") }),
        Object.assign({ responseType: "text" }, jsonHeaders())
    );

    if (createRes.status !== 201) {
        throw new Error(
            `setup: failed to create short URL, status ${createRes.status}: ${createRes.body}`
        );
    }

    const shortCode = createRes.json("shortCode");
    if (!shortCode) {
        throw new Error(`setup: response did not contain shortCode: ${createRes.body}`);
    }

    // Warm the Redis cache for this short code before load starts.
    http.get(`${BASE_URL}/${shortCode}`, {
        headers: { "User-Agent": CHROME_USER_AGENT },
        redirects: 0,
    });

    return { shortCode };
}

// ---------------------------------------------------------------------------
// Workloads
// ---------------------------------------------------------------------------

export function redirectWorkload(data) {
    const res = http.get(`${BASE_URL}/${data.shortCode}`, {
        headers: { "User-Agent": CHROME_USER_AGENT },
        redirects: 0,
        tags: { name: "redirect" },
    });

    redirectDuration.add(res.timings.duration);

    const passed = check(res, {
        "redirect status is 302": (r) => r.status === 302,
        "Location header present": (r) => !!findHeader(r, "Location"),
        "X-App-Instance header present": (r) => !!findHeader(r, "X-App-Instance"),
    });

    if (passed) {
        redirectChecksPassed.add(1);
    } else {
        redirectChecksFailed.add(1);
    }

    sleep(REDIRECT_SLEEP_SECONDS);
}

export function createWorkload() {
    const res = http.post(
        `${BASE_URL}/api/v1/urls`,
        JSON.stringify({ originalUrl: uniqueOriginalUrl("load") }),
        Object.assign({ tags: { name: "create" } }, jsonHeaders())
    );

    creationDuration.add(res.timings.duration);

    check(res, {
        "create status is 201": (r) => r.status === 201,
    });

    sleep(CREATE_SLEEP_SECONDS);
}

export function analyticsWorkload(data) {
    const res = http.get(`${BASE_URL}/api/v1/urls/${data.shortCode}/analytics`, {
        tags: { name: "analytics" },
    });

    analyticsDuration.add(res.timings.duration);

    check(res, {
        "analytics status is 200": (r) => r.status === 200,
    });

    sleep(ANALYTICS_SLEEP_SECONDS);
}
