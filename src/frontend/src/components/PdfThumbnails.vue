<script setup lang="ts">
import { type ComponentPublicInstance, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { pdfjsLib, type PDFDocumentProxy } from "@/lib/pdf";
import {
    THUMBNAIL_RENDER_SCALE,
    THUMBNAIL_SKELETON_COUNT,
    getDevicePixelRatio,
} from "@/lib/viewer";

const props = defineProps<{ url: string; currentPage: number }>();
const emit = defineEmits<{ navigate: [pageIndex: number] }>();

const thumbRefs = ref<HTMLElement[]>([]);
const canvasRefs = ref<HTMLCanvasElement[]>([]);
const totalPages = ref(0);
const loading = ref(true);
let loadVersion = 0;
let pdfDoc: PDFDocumentProxy | null = null;

function isStale(version: number) {
    return version !== loadVersion;
}

function destroyCurrentDocument() {
    const doc = pdfDoc;
    pdfDoc = null;
    if (doc) void doc.destroy();
}

function resetThumbnails() {
    totalPages.value = 0;
    thumbRefs.value = [];
    canvasRefs.value = [];
}

function setThumbRef(el: Element | ComponentPublicInstance | null, index: number) {
    if (el) thumbRefs.value[index] = el as HTMLElement;
}

function setCanvasRef(el: Element | ComponentPublicInstance | null, index: number) {
    if (el) canvasRefs.value[index] = el as HTMLCanvasElement;
}

async function loadThumbnails() {
    const version = ++loadVersion;
    destroyCurrentDocument();
    loading.value = true;
    resetThumbnails();

    try {
        const loadedDocument = await pdfjsLib.getDocument(props.url).promise;
        if (isStale(version)) {
            void loadedDocument.destroy();
            return;
        }

        pdfDoc = loadedDocument;
        totalPages.value = loadedDocument.numPages;
        loading.value = false;

        await nextTick();
        if (isStale(version)) return;

        await Promise.all(
            Array.from({ length: loadedDocument.numPages }, async (_, i) => {
                const page = await loadedDocument.getPage(i + 1);
                if (isStale(version)) return;

                const viewport = page.getViewport({
                    scale: THUMBNAIL_RENDER_SCALE * getDevicePixelRatio(),
                });
                const canvas = canvasRefs.value[i];
                if (!canvas) return;

                canvas.width = viewport.width;
                canvas.height = viewport.height;
                canvas.style.width = "100%";
                canvas.style.height = "auto";

                const context = canvas.getContext("2d");
                if (!context) return;

                await page.render({ canvasContext: context, viewport, canvas }).promise;
            }),
        );
    } catch {
        if (!isStale(version)) {
            destroyCurrentDocument();
            resetThumbnails();
            loading.value = false;
        }
    }
}

watch(
    () => props.currentPage,
    (page) => {
        thumbRefs.value[page - 1]?.scrollIntoView({ block: "nearest" });
    },
);

onMounted(loadThumbnails);
watch(() => props.url, loadThumbnails);
onUnmounted(() => {
    loadVersion++;
    destroyCurrentDocument();
    resetThumbnails();
});
</script>

<template>
    <div class="flex flex-col gap-2 px-2 py-2">
        <template v-if="loading">
            <div
                v-for="n in THUMBNAIL_SKELETON_COUNT"
                :key="n"
                class="w-full aspect-[3/4] rounded bg-muted animate-pulse"
            />
        </template>

        <template v-else>
            <button
                v-for="i in totalPages"
                :key="i"
                :ref="(el) => setThumbRef(el, i - 1)"
                class="flex flex-col items-center gap-1 rounded p-1 transition-colors hover:bg-muted/60 focus:outline-none"
                :class="currentPage === i ? 'bg-muted ring-1 ring-primary' : ''"
                @click="emit('navigate', i - 1)"
            >
                <canvas
                    :ref="(el) => setCanvasRef(el, i - 1)"
                    class="w-full rounded shadow-sm border block"
                />
                <span class="text-[10px] text-muted-foreground tabular-nums">{{ i }}</span>
            </button>
        </template>
    </div>
</template>
