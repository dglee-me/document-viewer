<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { toast } from "vue-sonner";
import { useDebounceFn } from "@vueuse/core";
import { pdfjsLib, type PDFDocumentProxy, type PDFPageProxy, type RenderTask } from "@/lib/pdf";
import {
    CSS_BASE_SCALE,
    FIT_SAFETY_PX,
    MAX_PAGE_CANVAS_EDGE_PX,
    MAX_PAGE_CANVAS_PIXELS,
    MIN_FIT_SCALE,
    PAGE_VISIBILITY_THRESHOLD_STEPS,
    RESIZE_DEBOUNCE_MS,
    getDevicePixelRatio,
} from "@/lib/viewer";

// --- types ---

type PageRef = { num: number; gen: number };

type RenderedPage = {
    page: PDFPageProxy;
    canvas: HTMLCanvasElement;
    baseCssWidth: number;
    baseCssHeight: number;
    renderTask: RenderTask | null;
    renderPromise: Promise<void> | null;
    renderSerial: number;
    renderedScale: number;
};

// --- props / emits ---

const props = defineProps<{
    url: string;
    zoomMode: "fit" | "manual";
    zoomLevel: number;
}>();

const emit = defineEmits<{
    pageChange: [page: number, total: number];
    outline: [items: unknown[]];
}>();

// --- state ---

const container = ref<HTMLDivElement>();

let pdfDoc: PDFDocumentProxy | null = null;
let mainObserver: ResizeObserver | null = null;
let loadVersion = 0;
let renderVersion = 0;

// 의도적으로 비반응형 — imperative canvas sizing 전용
const renderedPages: RenderedPage[] = [];

const PAGE_CANVAS_CLASS = "mx-auto block max-w-none shadow-sm border rounded-lg";
const PAGE_VISIBILITY_THRESHOLDS = Array.from(
    { length: PAGE_VISIBILITY_THRESHOLD_STEPS + 1 },
    (_, i) => i / PAGE_VISIBILITY_THRESHOLD_STEPS,
);

// --- stale checks ---

function isStale(version: number) {
    return version !== loadVersion || !container.value;
}

// --- document lifecycle ---

function destroyCurrentDocument() {
    const doc = pdfDoc;
    pdfDoc = null;
    if (doc) void doc.destroy();
}

function resetRenderedPages() {
    const pages = [...renderedPages];
    renderVersion++;
    observer?.disconnect();
    pageVisibility.clear();
    mainObserver?.disconnect();
    mainObserver = null;
    renderedPages.length = 0;
    void Promise.all(pages.map(cancelPageRender));
    if (container.value) container.value.innerHTML = "";
}

// --- scale helpers ---

function calcFitScale(baseCssWidth: number): number {
    const mainEl = container.value?.closest("main");
    const containerWidth = container.value?.getBoundingClientRect().width;
    const availableWidth = Math.floor(
        containerWidth || mainEl?.clientWidth || document.documentElement.clientWidth,
    );
    const safeWidth = Math.max(availableWidth - FIT_SAFETY_PX, 1);
    return Math.max(safeWidth / baseCssWidth, MIN_FIT_SCALE);
}

function getWidestBaseCssWidth(): number {
    return renderedPages.reduce((widest, page) => Math.max(widest, page.baseCssWidth), 0);
}

function calcEffectiveScale(): number {
    const baseFit = calcFitScale(getWidestBaseCssWidth());
    return props.zoomMode === "fit" ? baseFit : baseFit * props.zoomLevel;
}

function calcCanvasRenderScale(effectiveScale: number): number {
    return CSS_BASE_SCALE * effectiveScale * getDevicePixelRatio();
}

function clampCanvasRenderScale(page: RenderedPage, desiredScale: number): number {
    const viewport = page.page.getViewport({ scale: desiredScale });
    const edgeRatio = MAX_PAGE_CANVAS_EDGE_PX / Math.max(viewport.width, viewport.height);
    const areaRatio = Math.sqrt(MAX_PAGE_CANVAS_PIXELS / (viewport.width * viewport.height));
    return desiredScale * Math.min(1, edgeRatio, areaRatio);
}

// --- render ---

function isRenderCanceled(error: unknown): boolean {
    return error instanceof Error && error.name === "RenderingCancelledException";
}

function setPageDisplaySize(page: RenderedPage, effectiveScale: number) {
    page.canvas.style.width = page.baseCssWidth * effectiveScale + "px";
    page.canvas.style.height = page.baseCssHeight * effectiveScale + "px";
}

async function cancelPageRender(page: RenderedPage) {
    const task = page.renderTask;
    const promise = page.renderPromise;
    page.renderSerial++;

    if (!task || !promise) return;

    task.cancel();
    try {
        await promise;
    } catch (error) {
        if (!isRenderCanceled(error)) console.error(error);
    } finally {
        if (page.renderTask === task) {
            page.renderTask = null;
            page.renderPromise = null;
        }
    }
}

async function renderPage(page: RenderedPage, effectiveScale: number, version: number) {
    const renderScale = clampCanvasRenderScale(page, calcCanvasRenderScale(effectiveScale));
    const viewport = page.page.getViewport({ scale: renderScale });
    const pixelWidth = Math.ceil(viewport.width);
    const pixelHeight = Math.ceil(viewport.height);

    setPageDisplaySize(page, effectiveScale);

    if (
        page.renderedScale === renderScale &&
        page.canvas.width === pixelWidth &&
        page.canvas.height === pixelHeight
    ) {
        return;
    }

    await cancelPageRender(page);
    const serial = ++page.renderSerial;
    if (serial !== page.renderSerial || version !== renderVersion || !container.value) return;

    page.canvas.width = pixelWidth;
    page.canvas.height = pixelHeight;

    const context = page.canvas.getContext("2d", { alpha: false });
    if (!context) return;

    const task = page.page.render({ canvasContext: context, viewport, canvas: page.canvas });
    page.renderTask = task;
    page.renderPromise = task.promise;

    try {
        await task.promise;
        if (serial === page.renderSerial && version === renderVersion && container.value) {
            page.renderedScale = renderScale;
        }
    } catch (error) {
        if (!isRenderCanceled(error)) throw error;
    } finally {
        if (page.renderTask === task) {
            page.renderTask = null;
            page.renderPromise = null;
        }
    }
}

// --- scale application ---

function applyScaleToRenderedPages(scale: number) {
    renderedPages.forEach((page) => setPageDisplaySize(page, scale));
}

function resizeCanvasesToContainer(rerender = true) {
    if (renderedPages.length === 0) return;
    if (props.zoomMode !== "fit") return;
    const fitScale = calcFitScale(getWidestBaseCssWidth());
    applyScaleToRenderedPages(fitScale);
    if (rerender) debouncedRenderCurrentScale();
}

function applyCurrentScale() {
    if (renderedPages.length === 0) return;
    const scale = calcEffectiveScale();
    applyScaleToRenderedPages(scale);
    debouncedRenderCurrentScale();
}

async function renderAllPagesAtCurrentScale() {
    if (renderedPages.length === 0) return;
    const version = ++renderVersion;
    const scale = calcEffectiveScale();
    applyScaleToRenderedPages(scale);

    for (const page of renderedPages) {
        if (version !== renderVersion || !container.value) return;
        await renderPage(page, scale, version);
    }
}

// --- intersection observer (page visibility tracking) ---

const pageVisibility = new Map<number, number>();
let observer: IntersectionObserver | null = null;

function setupIntersectionObserver(total: number) {
    observer?.disconnect();
    pageVisibility.clear();

    observer = new IntersectionObserver(
        (entries) => {
            for (const entry of entries) {
                const idx = Number((entry.target as HTMLElement).dataset.page);
                pageVisibility.set(idx, entry.intersectionRatio);
            }
            let maxRatio = -1;
            let currentPage = 1;
            pageVisibility.forEach((ratio, idx) => {
                if (ratio > maxRatio) {
                    maxRatio = ratio;
                    currentPage = idx + 1;
                }
            });
            emit("pageChange", currentPage, total);
        },
        { threshold: PAGE_VISIBILITY_THRESHOLDS },
    );

    container.value?.querySelectorAll("canvas").forEach((canvas) => {
        observer!.observe(canvas);
    });
}

// --- PDF loading ---

async function loadOutline(doc: PDFDocumentProxy, version: number) {
    try {
        const outline = await doc.getOutline();
        if (version === loadVersion) emit("outline", outline ?? []);
    } catch (e) {
        if (version === loadVersion) emit("outline", []);
        console.error(e);
    }
}

function createPageCanvas(
    pageNum: number,
    baseCssWidth: number,
    baseCssHeight: number,
    effectiveScale: number,
): HTMLCanvasElement {
    const canvas = document.createElement("canvas");
    canvas.style.width = baseCssWidth * effectiveScale + "px";
    canvas.style.height = baseCssHeight * effectiveScale + "px";
    canvas.className = PAGE_CANVAS_CLASS;
    canvas.dataset.page = String(pageNum - 1);
    return canvas;
}

async function loadDocumentPages(
    doc: PDFDocumentProxy,
    version: number,
    firstPage: PDFPageProxy,
): Promise<boolean> {
    let widestBaseCssWidth = firstPage.getViewport({ scale: 1 }).width * CSS_BASE_SCALE;
    let fitScale = calcFitScale(widestBaseCssWidth);

    for (let i = 1; i <= doc.numPages; i++) {
        const page = i === 1 ? firstPage : await doc.getPage(i);
        if (isStale(version)) return false;

        const baseViewport = page.getViewport({ scale: 1 });
        const baseCssWidth = baseViewport.width * CSS_BASE_SCALE;
        const baseCssHeight = baseViewport.height * CSS_BASE_SCALE;

        if (baseCssWidth > widestBaseCssWidth) {
            widestBaseCssWidth = baseCssWidth;
            fitScale = calcFitScale(widestBaseCssWidth);
            resizeCanvasesToContainer(false);
        }

        const effectiveScale = props.zoomMode === "fit" ? fitScale : fitScale * props.zoomLevel;
        const canvas = createPageCanvas(i, baseCssWidth, baseCssHeight, effectiveScale);
        container.value!.appendChild(canvas);

        const renderedPage: RenderedPage = {
            page,
            canvas,
            baseCssWidth,
            baseCssHeight,
            renderTask: null,
            renderPromise: null,
            renderSerial: 0,
            renderedScale: 0,
        };
        renderedPages.push(renderedPage);

        await renderPage(renderedPage, effectiveScale, renderVersion);
        if (isStale(version)) return false;
    }

    return true;
}

function setupObservers(numPages: number) {
    setupIntersectionObserver(numPages);

    mainObserver?.disconnect();
    const mainEl = container.value?.closest("main");
    if (mainEl) {
        mainObserver = new ResizeObserver(debouncedResize);
        mainObserver.observe(mainEl);
    }
}

async function loadDocument() {
    if (!container.value) return;
    const version = ++loadVersion;
    resetRenderedPages();
    destroyCurrentDocument();

    const toastId = toast.loading("PDF 로딩 중...");
    try {
        const doc = await pdfjsLib.getDocument(props.url).promise;
        if (isStale(version)) {
            void doc.destroy();
            return;
        }

        pdfDoc = doc;
        emit("pageChange", 1, doc.numPages);
        void loadOutline(doc, version);

        // 첫 페이지에서 표시 폭을 먼저 계산해 초기 렌더링 깜빡임을 줄인다.
        await nextTick();
        if (isStale(version)) return;

        const firstPage = await doc.getPage(1);
        if (isStale(version)) return;

        const loaded = await loadDocumentPages(doc, version, firstPage);
        if (!loaded) return;

        emit("pageChange", 1, doc.numPages);
        setupObservers(doc.numPages);
    } catch (e) {
        if (version === loadVersion) {
            resetRenderedPages();
            destroyCurrentDocument();
            toast.error("PDF를 불러올 수 없습니다.");
            console.error(e);
        }
    } finally {
        toast.dismiss(toastId);
    }
}

// --- navigation ---

function scrollToPage(pageIndex: number) {
    const canvas = container.value?.querySelector(`canvas[data-page="${pageIndex}"]`);
    canvas?.scrollIntoView({ block: "start" });
}

function isPageRef(value: unknown): value is PageRef {
    return typeof value === "object" && value !== null && "num" in value && "gen" in value;
}

async function navigateToDest(dest: string | unknown[] | null) {
    if (!pdfDoc || !dest) return;
    try {
        const resolved = typeof dest === "string" ? await pdfDoc.getDestination(dest) : dest;
        if (!resolved) return;
        const pageRef = resolved[0];
        if (!isPageRef(pageRef)) return;
        const pageIndex = await pdfDoc.getPageIndex(pageRef);
        scrollToPage(pageIndex);
    } catch {
        // dest 해석 실패 무시
    }
}

defineExpose({ scrollToPage, navigateToDest });

// --- debounced handlers ---

const debouncedResize = useDebounceFn(() => {
    resizeCanvasesToContainer();
}, RESIZE_DEBOUNCE_MS);

const debouncedRenderCurrentScale = useDebounceFn(() => {
    void renderAllPagesAtCurrentScale().catch((error) => {
        if (!isRenderCanceled(error)) console.error(error);
    });
}, RESIZE_DEBOUNCE_MS);

// --- lifecycle ---

onMounted(loadDocument);
watch(() => props.url, loadDocument);
watch(() => [props.zoomMode, props.zoomLevel] as const, applyCurrentScale);
onUnmounted(() => {
    loadVersion++;
    resetRenderedPages();
    destroyCurrentDocument();
});
</script>

<template>
    <div ref="container" class="w-full min-w-0 space-y-4" />
</template>
