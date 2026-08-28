import app = require("app");
import device = require("device");
import toast = require("toast");

async function main(): Promise<void> {
  const installed = await app.isInstalled(app.packageName, { timeoutMs: 3000 });
  const screenOn = await device.isScreenOn({ timeoutMs: 3000 });

  await toast.showToast("Node CommonJS TypeScript template works", {
    duration: "short",
    log: true,
    timeoutMs: 3000,
  });

  console.log(`package=${app.packageName} installed=${installed} screenOn=${screenOn}`);
}

void main();
