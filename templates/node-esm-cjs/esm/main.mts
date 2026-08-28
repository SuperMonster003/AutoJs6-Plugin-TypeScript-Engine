import app from "app";
import device from "device";
import toast from "toast";

const installed = await app.isInstalled(app.packageName, { timeoutMs: 3000 });
const screenOn = await device.isScreenOn({ timeoutMs: 3000 });

await toast.showToast("Node ESM TypeScript template works", {
  duration: "short",
  log: true,
  timeoutMs: 3000,
});

console.log(`package=${app.packageName} installed=${installed} screenOn=${screenOn}`);
