// Firestore rules for community_votes, exercised exactly as the app calls them: unauthenticated REST commits.
// Run: cd backend && npm test   (starts the Firestore emulator for the demo project; nothing touches a real project)
import { test, before } from "node:test";
import assert from "node:assert/strict";

const HOST = process.env.FIRESTORE_EMULATOR_HOST;
const PROJECT = process.env.GCLOUD_PROJECT || "demo-biblescroll";
if (!HOST) throw new Error("Run through `firebase emulators:exec` (npm test): FIRESTORE_EMULATOR_HOST is not set");

const ROOT = `projects/${PROJECT}/databases/(default)/documents`;
const BASE = `http://${HOST}/v1/${ROOT}`;
const doc = (id) => `${ROOT}/community_votes/${id}`;
const HOUR = 3600 * 1000;
const ts = (ms) => new Date(ms).toISOString();

async function commit(writes, { admin = false } = {}) {
  const headers = { "Content-Type": "application/json" };
  if (admin) headers.Authorization = "Bearer owner"; // emulator-only: bypasses rules, like the seed script's admin token
  const res = await fetch(`${BASE}:commit`, { method: "POST", headers, body: JSON.stringify({ writes }) });
  return { status: res.status, body: await res.json() };
}

/** The app's write: increment `count` by [by] on an existing document. */
const increment = (id, by = 1) => ({
  transform: { document: doc(id), fieldTransforms: [{ fieldPath: "count", increment: { integerValue: String(by) } }] },
  currentDocument: { exists: true },
});

async function seed(id, { count = 0, opensAt = Date.now() - HOUR, closesAt = Date.now() + HOUR } = {}) {
  const r = await commit([{
    update: {
      name: doc(id),
      fields: {
        count: { integerValue: String(count) },
        opensAt: { timestampValue: ts(opensAt) },
        closesAt: { timestampValue: ts(closesAt) },
      },
    },
  }], { admin: true });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function countOf(id) {
  const res = await fetch(`${BASE}/community_votes/${id}`); // unauthenticated read
  assert.equal(res.status, 200);
  return Number((await res.json()).fields.count.integerValue);
}

const denied = (r) => assert.equal(r.status, 403, `expected PERMISSION_DENIED, got ${r.status} ${JSON.stringify(r.body)}`);
let n = 0;
const fresh = () => `2026-12_test-${++n}`;

before(async () => {
  await fetch(`http://${HOST}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, { method: "DELETE" });
});

test("+1 increment transform inside the window is accepted", async () => {
  const id = fresh();
  await seed(id, { count: 4 });
  const r = await commit([increment(id)]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(await countOf(id), 5);
});

test("two +1 votes in a row both count", async () => {
  const id = fresh();
  await seed(id);
  assert.equal((await commit([increment(id)])).status, 200);
  assert.equal((await commit([increment(id)])).status, 200);
  assert.equal(await countOf(id), 2);
});

test("+2 is denied", async () => {
  const id = fresh();
  await seed(id, { count: 3 });
  denied(await commit([increment(id, 2)]));
  assert.equal(await countOf(id), 3);
});

test("a decrement is denied", async () => {
  const id = fresh();
  await seed(id, { count: 3 });
  denied(await commit([increment(id, -1)]));
});

test("+0 is denied", async () => {
  const id = fresh();
  await seed(id, { count: 3 });
  denied(await commit([increment(id, 0)]));
});

test("a reset (count set to 0) is denied", async () => {
  const id = fresh();
  await seed(id, { count: 7 });
  denied(await commit([{ update: { name: doc(id), fields: { count: { integerValue: "0" } } }, updateMask: { fieldPaths: ["count"] } }]));
  assert.equal(await countOf(id), 7);
});

test("changing opensAt is denied, alone or with a valid +1", async () => {
  const id = fresh();
  await seed(id);
  const earlier = { timestampValue: ts(Date.now() - 48 * HOUR) };
  denied(await commit([{ update: { name: doc(id), fields: { opensAt: earlier } }, updateMask: { fieldPaths: ["opensAt"] } }]));
  denied(await commit([
    { update: { name: doc(id), fields: { opensAt: earlier } }, updateMask: { fieldPaths: ["opensAt"] } },
    increment(id),
  ]));
  assert.equal(await countOf(id), 0); // the commit is atomic: the +1 didn't land either
});

test("adding another field with a +1 is denied", async () => {
  const id = fresh();
  await seed(id);
  denied(await commit([{
    update: { name: doc(id), fields: { note: { stringValue: "hi" } } },
    updateMask: { fieldPaths: ["note"] },
    updateTransforms: [{ fieldPath: "count", increment: { integerValue: "1" } }],
  }]));
});

test("creating a document is denied (plain create and increment on a missing doc)", async () => {
  const id = fresh();
  denied(await commit([{
    update: { name: doc(id), fields: { count: { integerValue: "1" }, opensAt: { timestampValue: ts(0) }, closesAt: { timestampValue: ts(Date.now() + HOUR) } } },
    currentDocument: { exists: false },
  }]));
  denied(await commit([{ transform: { document: doc(id), fieldTransforms: [{ fieldPath: "count", increment: { integerValue: "1" } }] } }]));
  const res = await fetch(`${BASE}/community_votes/${id}`);
  assert.equal(res.status, 404);
});

test("deleting is denied", async () => {
  const id = fresh();
  await seed(id, { count: 2 });
  denied(await commit([{ delete: doc(id) }]));
  assert.equal(await countOf(id), 2);
});

test("+1 before the window opens is denied", async () => {
  const id = fresh();
  await seed(id, { opensAt: Date.now() + HOUR, closesAt: Date.now() + 2 * HOUR });
  denied(await commit([increment(id)]));
});

test("+1 after the window closes is denied", async () => {
  const id = fresh();
  await seed(id, { opensAt: Date.now() - 2 * HOUR, closesAt: Date.now() - HOUR });
  denied(await commit([increment(id)]));
});

test("anyone can read and list the counters", async () => {
  const id = fresh();
  await seed(id, { count: 9 });
  assert.equal(await countOf(id), 9);
  const res = await fetch(`${BASE}/community_votes?pageSize=100`);
  assert.equal(res.status, 200);
});

test("nothing outside community_votes is readable or writable", async () => {
  denied(await commit([{ update: { name: `${ROOT}/other/x`, fields: { a: { integerValue: "1" } } } }]));
  assert.equal((await fetch(`${BASE}/other/x`)).status, 403);
});

test("documented limit: a plain update to count+1 also passes (rules see only the result)", async () => {
  // Equivalent to a +1 for the counter; clients race-safely use the increment transform instead.
  const id = fresh();
  await seed(id, { count: 1 });
  const r = await commit([{ update: { name: doc(id), fields: { count: { integerValue: "2" } } }, updateMask: { fieldPaths: ["count"] } }]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
});
