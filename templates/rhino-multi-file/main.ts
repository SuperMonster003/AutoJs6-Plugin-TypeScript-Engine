import { readDeviceReport } from "@/lib/device-report";
import { formatReport } from "@/lib/format-report";

const report = readDeviceReport();
const message = formatReport(report);

console.log(message);
console.log(report.summary);
toast(message);
