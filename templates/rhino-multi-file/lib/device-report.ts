export interface DeviceReport {
  readonly dimensions: string;
  readonly sdk: number;
  readonly summary: string;
}

export function readDeviceReport(): DeviceReport {
  return {
    dimensions: `${device.width}x${device.height}`,
    sdk: device.sdkInt,
    summary: device.summary(),
  };
}
