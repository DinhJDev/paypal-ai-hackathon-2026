import { randomUUID } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const calendarDirectory = resolve(process.cwd(), "node_modules/@bryntum/calendar");
const metadataPath = resolve(calendarDirectory, "package.json");
if (!existsSync(metadataPath)) process.exit(0);

const metadata = JSON.parse(readFileSync(metadataPath, "utf8"));
if (metadata.name !== "@bryntum/calendar-trial") process.exit(0);

const bundleNames = ["calendar.module.js", "calendar.umd.js"];
const bundles = bundleNames
  .map(name => ({ path: resolve(calendarDirectory, name), content: readFileSync(resolve(calendarDirectory, name), "utf8") }))
  .filter(bundle => bundle.content.includes("%LICENSE%"));

if (bundles.length === 0) process.exit(0);

const trialId = randomUUID();
for (const bundle of bundles) {
  writeFileSync(bundle.path, bundle.content.replaceAll("%LICENSE%", trialId), "utf8");
}

console.log(`Initialized Bryntum Calendar trial markers in ${bundles.length} bundle(s).`);
