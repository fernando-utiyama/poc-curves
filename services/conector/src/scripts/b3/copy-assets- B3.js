const { cpSync, existsSync } = require("fs");
const path = require("path");

const projectRoot = path.resolve(__dirname, "../../../..");

const assets = [
  {
    source: path.join(projectRoot, "src", "functions", "b3", "layouts"),
    target: path.join(projectRoot, "dist", "src", "functions", "b3", "layouts"),
  },
];

for (const asset of assets) {
  if (!existsSync(asset.source)) {
    continue;
  }

  cpSync(asset.source, asset.target, { recursive: true });
}
