import type { DeviceReport } from "./device-report";

export function formatReport(report: DeviceReport): string {
  return `AutoJs6 TypeScript: ${report.dimensions}, API ${report.sdk}`;
}
