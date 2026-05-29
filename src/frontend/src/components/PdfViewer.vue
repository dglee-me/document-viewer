<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { toast } from "vue-sonner";
import { useDebounceFn } from "@vueuse/core";
import { pdfjsLib, type PDFDocumentProxy } from "@/lib/pdf";
import {
    CSS_BASE_SCALE,
    FIT_SAFETY_PX,
    MIN_FIT_SCALE,
    PAGE_VISIBILITY_THRESHOLD_STEPS,
    RESIZE_DEBOUNCE_MS,
    getDevicePixelRatio,
} from "@/lib/viewer";

const props = defineProps<{
    url: string;
    zoomMode: "fit" | "manual";
    zoomLevel: number;
}>();
const emit = defineEmits<{
    pageChange: [page: number, total: number];
    outline: [items: unknown[]];
}>();
const container = ref<HTMLDivElement>();
let pdfDoc: PDFDocumentProxy | null = null;
let mainObserver: ResizeObserver | null = null;
let loadVersion = 0;
type PageRef = { num: number; gen: number };
const PAGE_CANVAS_CLASS = "mx-auto block max-w-none shadow-sm border rounded-lg";
const PAGE_VISIBILITY_THRESHOLDS = Array.from(
    { length: PAGE_VISIBILITY_THRESHOLD_STEPS + 1 },
    (_, i) => i / PAGE_VISIBILITY_THRESHOLD_STEPS,
);

const renderScale = getDevicePixelRatio() * CSS_BASE_SCALE;
// 의도적으로 비반응형 — imperative canvas sizing 전용
const pageBaseCssWidths: number[] = [];

function isStale(version: number) {
    return version !== loadVersion || !container.value;
}

function destroyCurrentDocument() {
    const document = pdfDoc;
    pdfDoc = null;
    if (document) void document.destroy();
}

function resetRenderedPages() {
    observer?.disconnect();
    pageVisibility.clear();
    mainObserver?.disconnect();
    mainObserver = null;
    pageBaseCssWidths.length = 0;
    if (container.value) container.value.innerHTML = "";
}

function calcFitScale(baseCssWidth: number): number {
    const mainEl = container.value?.closest("main");
    const containerWidth = container.value?.getBoundingClientRect().width;
    const availableWidth = Math.floor(
        containerWidth || mainEl?.clientWidth || document.documentElement.clientWidth,
    );
    const safeWidth = Math.max(availableWidth - FIT_SAFETY_PX, 1);
    return Math.max(safeWidth / baseCssWidth, MIN_FIT_SCALE);
}

// --- IntersectionObserver (동적 canvas → 수동 관리) ---

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

function calcEffectiveScale(): number {
    const baseFit = calcFitScale(Math.max(...pageBaseCssWidths));
    return props.zoomMode === "fit" ? baseFit : baseFit * props.zoomLevel;
}

function resizeCanvasesToContainer() {
    if (pageBaseCssWidths.length === 0) return;
    if (props.zoomMode !== "fit") return;
    const fitScale = calcFitScale(Math.max(...pageBaseCssWidths));
    container.value?.querySelectorAll<HTMLCanvasElement>("canvas").forEach((canvas, i) => {
        canvas.style.width = pageBaseCssWidths[i] * fitScale + "px";
    });
}

function applyCurrentScale() {
    if (pageBaseCssWidths.length === 0) return;
    const scale = calcEffectiveScale();
    container.value?.querySelectorAll<HTMLCanvasElement>("canvas").forEach((canvas, i) => {
        canvas.style.width = pageBaseCssWidths[i] * scale + "px";
    });
}

// --- PDF 로딩 ---

async function loadOutline(doc: PDFDocumentProxy, version: number) {
    try {
        const outline = await doc.getOutline();
        if (version === loadVersion) emit("outline", outline ?? []);
    } catch (e) {
        if (version === loadVersion) emit("outline", []);
        console.error(e);
    }
}

async function loadDocument() {
    if (!container.value) return;
    const version = ++loadVersion;
    destroyCurrentDocument();
    resetRenderedPages();

    const toastId = toast.loading("PDF 로딩 중...");
    try {
        const loadedDocument = await pdfjsLib.getDocument(props.url).promise;
        if (isStale(version)) {
            void loadedDocument.destroy();
            return;
        }

        pdfDoc = loadedDocument;
        emit("pageChange", 1, loadedDocument.numPages);
        void loadOutline(loadedDocument, version);

        // 첫 페이지에서 표시 폭을 먼저 계산해 초기 렌더링 깜빡임을 줄인다.
        await nextTick();
        if (isStale(version)) return;

        const firstPage = await loadedDocument.getPage(1);
        if (isStale(version)) return;

        const firstBaseCssWidth = firstPage.getViewport({ scale: 1 }).width * CSS_BASE_SCALE;
        let widestBaseCssWidth = firstBaseCssWidth;
        let fitScale = calcFitScale(widestBaseCssWidth);

        for (let i = 1; i <= loadedDocument.numPages; i++) {
            const page = i === 1 ? firstPage : await loadedDocument.getPage(i);
            if (isStale(version)) return;

            const baseViewport = page.getViewport({ scale: 1 });
            const viewport = page.getViewport({ scale: renderScale });

            const baseCssWidth = baseViewport.width * CSS_BASE_SCALE;
            pageBaseCssWidths.push(baseCssWidth);
            if (baseCssWidth > widestBaseCssWidth) {
                widestBaseCssWidth = baseCssWidth;
                fitScale = calcFitScale(widestBaseCssWidth);
                resizeCanvasesToContainer();
            }

            const effectiveScale = props.zoomMode === "fit" ? fitScale : fitScale * props.zoomLevel;
            const canvas = document.createElement("canvas");
            canvas.width = viewport.width;
            canvas.height = viewport.height;
            canvas.style.width = baseCssWidth * effectiveScale + "px";
            canvas.style.height = "auto";
            canvas.className = PAGE_CANVAS_CLASS;
            canvas.dataset.page = String(i - 1);
            container.value.appendChild(canvas);

            await page.render({ canvasContext: canvas.getContext("2d")!, viewport, canvas })
                .promise;
            if (isStale(version)) return;
        }

        emit("pageChange", 1, loadedDocument.numPages);
        setupIntersectionObserver(loadedDocument.numPages);

        mainObserver?.disconnect();
        const mainEl = container.value?.closest("main");
        if (mainEl) {
            mainObserver = new ResizeObserver(debouncedResize);
            mainObserver.observe(mainEl);
        }
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

// --- resize → fit (main 엘리먼트 ResizeObserver로 sidebar toggle + window resize 모두 대응) ---

const debouncedResize = useDebounceFn(() => {
    resizeCanvasesToContainer();
}, RESIZE_DEBOUNCE_MS);

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
