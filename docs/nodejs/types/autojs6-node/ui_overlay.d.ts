declare module "ui.overlay" {
  export interface OverlayDescriptor {
    readonly content: Record<string, unknown>;
    readonly window?: OverlayWindowOptions;
    readonly disclosure: OverlayDisclosure;
  }

  export interface OverlayWindowOptions {
    readonly width?: number | "wrap_content" | "match_parent";
    readonly height?: number | "wrap_content" | "match_parent";
    readonly x?: number;
    readonly y?: number;
    readonly touchable?: boolean;
    readonly focusable?: boolean;
    readonly draggable?: boolean;
    readonly visible?: true;
    readonly alpha?: number;
  }

  export interface OverlayDisclosure {
    readonly title: string;
    readonly text: string;
  }

  export interface OverlayBounds {
    readonly x: number;
    readonly y: number;
    readonly width: number;
    readonly height: number;
  }

  export interface OverlaySnapshot {
    readonly id: string;
    readonly type: "overlay";
    readonly closed: boolean;
    readonly visible: boolean;
    readonly createdAt: number;
    readonly bounds?: OverlayBounds;
    readonly disclosure?: {
      readonly title: string;
    };
    readonly droppedCount?: number;
  }

  export interface OverlayEvent {
    readonly type: string;
    readonly overlayId: string;
    readonly targetId?: string;
    readonly eventTime: number;
    readonly reason?: string;
    readonly state?: Record<string, unknown>;
  }

  export interface OverlayRequestOptions {
    readonly timeoutMs?: number;
    readonly maxEvents?: number;
    readonly dryRun?: boolean;
    readonly signal?: AutoJs6Node.AbortSignalLike;
  }

  export interface OverlayCloseSummary {
    readonly closedCount: number;
    readonly remainingCount: number;
  }

  export interface OverlayPermissionSnapshot {
    readonly granted: boolean;
    readonly canOpenSettings: boolean;
    readonly reason?: string;
  }

  export interface OverlayPermissionSettingsResult {
    readonly opened: boolean;
    readonly canOpenSettings?: boolean;
    readonly dryRun?: boolean;
    readonly reason?: string;
  }

  export interface OverlayHandle {
    readonly id: string;
    readonly type: "overlay";
    readonly closed: boolean;
    update(patch: Record<string, unknown>, options?: OverlayRequestOptions): Promise<OverlaySnapshot>;
    drainEvents(options?: OverlayRequestOptions): Promise<readonly OverlayEvent[]>;
    close(options?: OverlayRequestOptions): Promise<boolean>;
  }

  export interface OverlayModule {
    readonly policy: {
      readonly maxActiveOverlays: 4;
      readonly maxDescriptorBytes: 65536;
      readonly maxDescriptorDepth: 16;
      readonly defaultEventQueueSize: 64;
      readonly maxEventQueueSize: 128;
      readonly defaultDrainBatchSize: 32;
      readonly maxDrainBatchSize: 64;
      readonly defaultTimeoutMs: 10000;
      readonly maxTimeoutMs: 60000;
      readonly minAlpha: 0.2;
    };
    show(descriptor: OverlayDescriptor, options?: OverlayRequestOptions): Promise<OverlayHandle>;
    closeAll(options?: OverlayRequestOptions): Promise<OverlayCloseSummary>;
    hasPermission(options?: OverlayRequestOptions): Promise<OverlayPermissionSnapshot>;
    openPermissionSettings(options?: OverlayRequestOptions): Promise<OverlayPermissionSettingsResult>;
  }

  const overlay: OverlayModule;
  export default overlay;
  export const policy: OverlayModule["policy"];
  export const show: OverlayModule["show"];
  export const closeAll: OverlayModule["closeAll"];
  export const hasPermission: OverlayModule["hasPermission"];
  export const openPermissionSettings: OverlayModule["openPermissionSettings"];
}
