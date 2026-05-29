<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { useFullscreen, useTitle } from "@vueuse/core";
import { type DocumentResponse, fetchDocument, getDocumentFileUrl } from "@/api/document";
import PdfViewer from "@/components/PdfViewer.vue";
import ViewerToolbar from "@/components/ViewerToolbar.vue";
import OutlinePanel, { type OutlineItem } from "@/components/OutlinePanel.vue";
import PdfThumbnails from "@/components/PdfThumbnails.vue";
import {
    SidebarContent,
    SidebarGroup,
    SidebarGroupContent,
    SidebarGroupLabel,
    SidebarHeader,
    SidebarInset,
    SidebarMenuSkeleton,
    SidebarProvider,
    SidebarSeparator,
} from "@/components/ui/sidebar";
import ViewerSidebar from "@/components/ViewerSidebar.vue";

const VIEWER_TABS = [
    { key: "outline", label: "목차" },
    { key: "thumbnails", label: "페이지" },
] as const;
const DEFAULT_VIEWER_TAB = "outline";
const EMPTY_OUTLINE_TAB = "thumbnails";
const OUTLINE_SKELETON_COUNT = 6;
const SIDEBAR_PROVIDER_STYLE =
    "--sidebar-width: 13rem; --sidebar-top: 3rem; height: 100svh; overflow: hidden; flex-direction: column;";

type ViewerTab = (typeof VIEWER_TABS)[number]["key"];

const route = useRoute();
const docInfo = ref<DocumentResponse | null>(null);

const id = computed(() => Number(route.params.id));
const fileUrl = computed(() => getDocumentFileUrl(id.value));

const { isFullscreen, toggle: toggleFullscreen } = useFullscreen(document.documentElement);

useTitle(computed(() => docInfo.value?.originalFilename ?? "문서 뷰어"));

const currentPage = ref(1);
const loadedTotalPages = ref(0);
const outline = ref<OutlineItem[]>([]);
const outlineLoaded = ref(false);
const pdfViewer = ref<InstanceType<typeof PdfViewer>>();
const activeTab = ref<ViewerTab>(DEFAULT_VIEWER_TAB);

const ZOOM_STEPS = [0.25, 0.5, 0.67, 0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0];
const zoomMode = ref<"fit" | "manual">("fit");
const zoomLevel = ref(1.0);

function zoomIn() {
    if (zoomMode.value === "fit") {
        zoomMode.value = "manual";
        zoomLevel.value = 1.0;
    }
    const next = ZOOM_STEPS.find((s) => s > zoomLevel.value + 0.001);
    if (next !== undefined) zoomLevel.value = next;
}

function zoomOut() {
    if (zoomMode.value === "fit") {
        zoomMode.value = "manual";
        zoomLevel.value = 1.0;
    }
    const prev = [...ZOOM_STEPS].reverse().find((s) => s < zoomLevel.value - 0.001);
    if (prev !== undefined) zoomLevel.value = prev;
}

function fitToWidth() {
    zoomMode.value = "fit";
    zoomLevel.value = 1.0;
}
const totalPages = computed(() => docInfo.value?.pageCount ?? loadedTotalPages.value);
const hasOutline = computed(() => outlineLoaded.value && outline.value.length > 0);
const shouldShowThumbnails = computed(
    () => outlineLoaded.value && (activeTab.value === "thumbnails" || !outline.value.length),
);

watch(
    id,
    async (documentId) => {
        docInfo.value = null;
        currentPage.value = 1;
        loadedTotalPages.value = 0;
        const fetched = await fetchDocument(documentId);
        if (documentId === id.value) {
            docInfo.value = fetched;
        }
    },
    { immediate: true },
);

function handlePageChange(page: number, total: number) {
    currentPage.value = page;
    loadedTotalPages.value = total;
}

function resetNavigationState() {
    outline.value = [];
    outlineLoaded.value = false;
    activeTab.value = DEFAULT_VIEWER_TAB;
}

watch(fileUrl, () => {
    resetNavigationState();
});

function handleOutline(items: unknown[]) {
    outline.value = items as OutlineItem[];
    outlineLoaded.value = true;
    activeTab.value = outline.value.length ? DEFAULT_VIEWER_TAB : EMPTY_OUTLINE_TAB;
}
</script>

<template>
    <SidebarProvider :default-open="true" :style="SIDEBAR_PROVIDER_STYLE">
        <ViewerToolbar
            :filename="docInfo?.originalFilename"
            :current-page="currentPage"
            :total-pages="totalPages"
            :is-fullscreen="isFullscreen"
            :download-url="fileUrl"
            :zoom-mode="zoomMode"
            :zoom-level="zoomLevel"
            @toggle-fullscreen="toggleFullscreen"
            @zoom-in="zoomIn"
            @zoom-out="zoomOut"
            @fit-to-width="fitToWidth"
        />

        <div class="flex flex-1 overflow-hidden">
            <ViewerSidebar collapsible="offcanvas">
                <SidebarHeader v-if="hasOutline" class="p-0">
                    <div class="flex border-b">
                        <button
                            v-for="tab in VIEWER_TABS"
                            :key="tab.key"
                            class="flex-1 py-2.5 text-xs font-medium transition-colors"
                            :class="
                                activeTab === tab.key
                                    ? 'text-foreground border-b-2 border-sidebar-primary -mb-px'
                                    : 'text-muted-foreground hover:text-foreground'
                            "
                            @click="activeTab = tab.key"
                        >
                            {{ tab.label }}
                        </button>
                    </div>
                </SidebarHeader>

                <SidebarContent>
                    <SidebarGroup v-if="!outlineLoaded">
                        <SidebarGroupLabel>목차</SidebarGroupLabel>
                        <SidebarSeparator />
                        <SidebarGroupContent class="px-2 py-2">
                            <div class="space-y-2">
                                <SidebarMenuSkeleton
                                    v-for="n in OUTLINE_SKELETON_COUNT"
                                    :key="n"
                                    class="h-7"
                                />
                            </div>
                        </SidebarGroupContent>
                    </SidebarGroup>

                    <SidebarGroup v-if="hasOutline" v-show="activeTab === 'outline'">
                        <SidebarGroupContent>
                            <OutlinePanel
                                :items="outline"
                                @navigate="pdfViewer?.navigateToDest($event)"
                            />
                        </SidebarGroupContent>
                    </SidebarGroup>

                    <SidebarGroup v-if="outlineLoaded" v-show="shouldShowThumbnails">
                        <template v-if="!outline.length">
                            <SidebarGroupLabel>페이지</SidebarGroupLabel>
                            <SidebarSeparator />
                        </template>
                        <SidebarGroupContent class="px-1">
                            <PdfThumbnails
                                :url="fileUrl"
                                :current-page="currentPage"
                                @navigate="pdfViewer?.scrollToPage($event)"
                            />
                        </SidebarGroupContent>
                    </SidebarGroup>
                </SidebarContent>
            </ViewerSidebar>

            <SidebarInset class="min-w-0 overflow-auto bg-muted/50">
                <div class="w-full min-w-0 px-3 py-4 sm:px-4 sm:py-6">
                    <PdfViewer
                        ref="pdfViewer"
                        :url="fileUrl"
                        :zoom-mode="zoomMode"
                        :zoom-level="zoomLevel"
                        @page-change="handlePageChange"
                        @outline="handleOutline"
                    />
                </div>
            </SidebarInset>
        </div>
    </SidebarProvider>
</template>
