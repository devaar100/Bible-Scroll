// The seed script against the emulator: creates the month's counters once, never resets them.
import { test } from "node:test";
import assert from "node:assert/strict";
import { featured, nextMonth, seedMonth, window } from "../scripts/seed-month.mjs";

const HOST = process.env.FIRESTORE_EMULATOR_HOST;
const PROJECT = process.env.GCLOUD_PROJECT || "demo-biblescroll";
const ROOT = `projects/${PROJECT}/databases/(default)/documents`;
const target = { root: ROOT, base: `http://${HOST}/v1/${ROOT}`, headers: { Authorization: "Bearer owner" } };

const MINISTRIES = {
  formatVersion: 1,
  ministries: [
    { id: "river", months_featured: ["2031-02", "2031-03"] },
    { id: "harbor", months_featured: ["2031-02"] },
    { id: "test-hill", months_featured: ["2031-02"], test: true },
  ],
};

const read = async (id) => (await fetch(`${target.base}/community_votes/${id}`)).json();

test("window is the UTC calendar month; nextMonth rolls over the year", () => {
  assert.deepEqual(window("2031-12"), { opensAt: new Date("2031-12-01T00:00:00Z"), closesAt: new Date("2032-01-01T00:00:00Z") });
  assert.deepEqual(window("2032-02").closesAt, new Date("2032-03-01T00:00:00Z")); // leap year
  assert.equal(nextMonth(new Date("2031-12-31T23:59:59Z")), "2032-01");
  assert.equal(nextMonth(new Date("2031-01-31T12:00:00Z")), "2031-02");
  assert.throws(() => window("2031-13"));
});

test("featured skips test entries unless asked, and checks ids", () => {
  assert.deepEqual(featured(MINISTRIES, "2031-02", false).map((m) => m.id), ["river", "harbor"]);
  assert.deepEqual(featured(MINISTRIES, "2031-02", true).map((m) => m.id), ["river", "harbor", "test-hill"]);
  assert.throws(() => featured({ formatVersion: 1, ministries: [{ id: "Bad Id", months_featured: ["2031-02"] }] }, "2031-02"));
});

test("dry run writes nothing", async () => {
  const results = await seedMonth({ month: "2031-04", ministries: { formatVersion: 1, ministries: [{ id: "river", months_featured: ["2031-04"] }] }, dryRun: true, target });
  assert.deepEqual(results, [{ id: "2031-04_river", result: "would create" }]);
  assert.equal((await fetch(`${target.base}/community_votes/2031-04_river`)).status, 404);
});

test("creates the month's counters at zero with the UTC window, then never overwrites them", async () => {
  const first = await seedMonth({ month: "2031-02", ministries: MINISTRIES, target });
  assert.deepEqual(first, [{ id: "2031-02_river", result: "created" }, { id: "2031-02_harbor", result: "created" }]);

  const doc = await read("2031-02_river");
  assert.equal(doc.fields.count.integerValue, "0");
  assert.equal(doc.fields.opensAt.timestampValue, "2031-02-01T00:00:00Z");
  assert.equal(doc.fields.closesAt.timestampValue, "2031-03-01T00:00:00Z");
  assert.equal((await fetch(`${target.base}/community_votes/2031-02_test-hill`)).status, 404);

  // Someone voted (admin write here, since 2031 isn't open yet); a second seed must not reset it.
  await fetch(`${target.base}:commit`, {
    method: "POST", headers: { "Content-Type": "application/json", ...target.headers },
    body: JSON.stringify({ writes: [{ transform: { document: `${ROOT}/community_votes/2031-02_river`, fieldTransforms: [{ fieldPath: "count", increment: { integerValue: "1" } }] } }] }),
  });
  const again = await seedMonth({ month: "2031-02", ministries: MINISTRIES, includeTest: true, target });
  assert.deepEqual(again.map((r) => r.result), ["exists", "exists", "created"]);
  assert.equal((await read("2031-02_river")).fields.count.integerValue, "1");
});
