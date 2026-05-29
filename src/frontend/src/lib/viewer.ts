export const CSS_BASE_SCALE = 1.5;
export const FIT_SAFETY_PX = 2;
export const MIN_FIT_SCALE = 0.01;
export const MAX_PAGE_CANVAS_EDGE_PX = 8192;
export const MAX_PAGE_CANVAS_PIXELS = 32_000_000;
export const RESIZE_DEBOUNCE_MS = 150;
export const PAGE_VISIBILITY_THRESHOLD_STEPS = 10;
export const THUMBNAIL_RENDER_SCALE = 0.18;
export const THUMBNAIL_SKELETON_COUNT = 5;
// DPR=1 외장 모니터에서 열화 방지: 최소 1.5x 렌더
export const MIN_RENDER_DPR = 1.5;

export function getDevicePixelRatio(): number {
    return Math.max(window.devicePixelRatio || 1, MIN_RENDER_DPR);
}
