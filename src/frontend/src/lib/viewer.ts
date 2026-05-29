export const CSS_BASE_SCALE = 1.5;
export const FIT_SAFETY_PX = 2;
export const MIN_FIT_SCALE = 0.01;
export const RESIZE_DEBOUNCE_MS = 150;
export const PAGE_VISIBILITY_THRESHOLD_STEPS = 10;
export const THUMBNAIL_RENDER_SCALE = 0.18;
export const THUMBNAIL_SKELETON_COUNT = 5;

export function getDevicePixelRatio(): number {
    return window.devicePixelRatio || 1;
}
