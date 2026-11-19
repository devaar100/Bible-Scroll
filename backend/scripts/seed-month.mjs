#!/usr/bin/env node
// Creates a month's Community vote counters: community_votes/{YYYY-MM_ministryId} = {count: 0, opensAt, closesAt}.
// Reads the same ministries.json the app downloads (pipeline/community/ministries.json). Never overwrites an
// existing document, so running it twice can't reset a count. Run it yourself; it needs admin credentials:
//
//   GOOGLE_APPLICATION_CREDENTIALS=/path/outside/the/repo/key.json node scripts/seed-month.mjs --project <id> [--month 2026-12] [--dry-run]
//
// The service-account key must stay outside the repo (backend/.gitignore blocks the usual names). The token it
// mints is used for these requests only and never printed. Against the emulator: --emulator (uses
// FIRESTORE_EMULATOR_HOST and the emulator's admin bypass; no credentials).
import { createSign } from "node:crypto";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_MINISTRIES = path.resolve(HERE, "../../pipeline/community/ministries.json");
const ID = /^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$/;
const MONTH = /^[0-9]{4}-(0[1-9]|1[0-2])$/;

/** "2026-12" -> { opensAt: 2026-12-01T00:00:00Z, closesAt: 2027-01-01T00:00:00Z }. Months are UTC. */
export function window(month) {
  if (!MONTH.test(month)) throw new Error(`Not a month (YYYY-MM): ${month}`);
  const [y, m] = month.split("-").map(Number);
  return { opensAt: new Date(Date.UTC(y, m - 1, 1)), closesAt: new Date(Date.UTC(y, m, 1)) };
}

/** The UTC month after [now]. */
export function nextMonth(now = new Date()) {
  const d = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1));
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}`;
}

export function featured(data, month, includeTest) {
  if (data?.formatVersion !== 1 || !Array.isArray(data.ministries)) throw new Error("ministries.json: expected formatVersion 1 and a ministries array");
  for (const m of data.ministries) {
    if (!ID.test(m.id ?? "")) throw new Error(`ministries.json: bad id ${JSON.stringify(m.id)}`);
    if (!Array.isArray(m.months_featured) || !m.months_featured.every((x) => MONTH.test(x))) throw new Error(`ministries.json: ${m.id}: bad months_featured`);
  }
  return data.ministries.filter((m) => m.months_featured.includes(month) && (includeTest || !m.test));
}

/** target: { base, headers }. Returns [{ id, result: "created" | "exists" | "would create" }]. */
export async function seedMonth({ month, ministries, includeTest = false, dryRun = false, target }) {
  const { opensAt, closesAt } = window(month);
  const results = [];
  for (const m of featured(ministries, month, includeTest)) {
    const docId = `${month}_${m.id}`;
    if (dryRun) {
      results.push({ id: docId, result: "would create" });
      continue;
    }
    const res = await fetch(`${target.base}:commit`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...target.headers },
      body: JSON.stringify({
        writes: [{
          update: {
            name: `${target.root}/community_votes/${docId}`,
            fields: {
              count: { integerValue: "0" },
              opensAt: { timestampValue: opensAt.toISOString() },
              closesAt: { timestampValue: closesAt.toISOString() },
            },
          },
          currentDocument: { exists: false }, // never overwrite: an existing count is left alone
        }],
      }),
    });
    if (res.ok) results.push({ id: docId, result: "created" });
    else {
      const body = await res.json().catch(() => ({}));
      const status = body?.error?.status;
      if (status === "ALREADY_EXISTS" || status === "FAILED_PRECONDITION") results.push({ id: docId, result: "exists" });
      else throw new Error(`${docId}: HTTP ${res.status} ${status ?? ""} ${body?.error?.message ?? ""}`.trim());
    }
  }
  return results;
}

/** OAuth token for a service account (JWT bearer grant). Scope: Firestore only. */
async function serviceAccountToken(keyPath) {
  const key = JSON.parse(readFileSync(keyPath, "utf8"));
  const now = Math.floor(Date.now() / 1000);
  const b64 = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
  const unsigned = `${b64({ alg: "RS256", typ: "JWT" })}.${b64({
    iss: key.client_email, scope: "https://www.googleapis.com/auth/datastore",
    aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 600,
  })}`;
  const signature = createSign("RSA-SHA256").update(unsigned).sign(key.private_key).toString("base64url");
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${unsigned}.${signature}` }),
  });
  if (!res.ok) throw new Error(`Token request failed: HTTP ${res.status} (check the key file and its IAM role)`);
  return (await res.json()).access_token;
}

function parseArgs(argv) {
  const args = { month: null, project: process.env.FIREBASE_PROJECT ?? null, ministries: DEFAULT_MINISTRIES, includeTest: false, dryRun: false, emulator: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--month") args.month = argv[++i];
    else if (a === "--project") args.project = argv[++i];
    else if (a === "--ministries") args.ministries = argv[++i];
    else if (a === "--include-test") args.includeTest = true;
    else if (a === "--dry-run") args.dryRun = true;
    else if (a === "--emulator") args.emulator = true;
    else throw new Error(`Unknown argument: ${a}`);
  }
  return args;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const month = args.month ?? nextMonth();
  if (!args.project) throw new Error("Pass --project <firebase project id> (or set FIREBASE_PROJECT)");
  const ministries = JSON.parse(readFileSync(args.ministries, "utf8"));
  const root = `projects/${args.project}/databases/(default)/documents`;
  let target;
  if (args.emulator) {
    const host = process.env.FIRESTORE_EMULATOR_HOST;
    if (!host) throw new Error("--emulator needs FIRESTORE_EMULATOR_HOST");
    target = { root, base: `http://${host}/v1/${root}`, headers: { Authorization: "Bearer owner" } };
  } else if (args.dryRun) {
    target = { root, base: "", headers: {} };
  } else {
    const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
    if (!keyPath) throw new Error("Set GOOGLE_APPLICATION_CREDENTIALS to a service-account key kept outside the repo");
    if (path.resolve(keyPath).startsWith(path.resolve(HERE, "../.."))) throw new Error("Refusing a key file inside the repo; move it elsewhere");
    target = { root, base: `https://firestore.googleapis.com/v1/${root}`, headers: { Authorization: `Bearer ${await serviceAccountToken(keyPath)}` } };
  }
  const results = await seedMonth({ month, ministries, includeTest: args.includeTest, dryRun: args.dryRun, target });
  const { opensAt, closesAt } = window(month);
  console.log(`${month}: opens ${opensAt.toISOString()}, closes ${closesAt.toISOString()} (UTC)`);
  if (!results.length) console.log("No ministries featured this month (test entries need --include-test).");
  for (const r of results) console.log(`  ${r.id}: ${r.result}`);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main().catch((e) => {
    console.error(e.message);
    process.exit(1);
  });
}
