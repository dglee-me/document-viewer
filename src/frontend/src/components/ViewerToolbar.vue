<script setup lang="ts">
import { computed } from "vue";
import {
    ArrowLeftRight,
    Download,
    Maximize2,
    Minimize2,
    Minus,
    Moon,
    Plus,
    Sun,
} from "@lucide/vue";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { useColorMode } from "@/composables/useColorMode";

const { isDark, toggleDark } = useColorMode();

const MAX_ZOOM = 3.0;
const MIN_ZOOM = 0.25;

const props = defineProps<{
    filename: string | undefined;
    currentPage: number;
    totalPages: number;
    isFullscreen: boolean;
    downloadUrl: string;
    zoomMode: "fit" | "manual";
    zoomLevel: number;
}>();

defineEmits<{
    toggleFullscreen: [];
    zoomIn: [];
    zoomOut: [];
    fitToWidth: [];
}>();

const canZoomIn = computed(() => props.zoomMode === "fit" || props.zoomLevel < MAX_ZOOM);
const canZoomOut = computed(() => props.zoomMode === "fit" || props.zoomLevel > MIN_ZOOM);
</script>

<template>
    <header
        class="sticky top-0 z-10 border-b bg-background flex items-center justify-between px-2 h-12 shrink-0"
    >
        <div class="flex items-center gap-1 min-w-0">
            <SidebarTrigger />
            <Separator orientation="vertical" class="h-5 mx-1" />
            <span class="text-sm font-medium truncate">{{ filename ?? "..." }}</span>
        </div>

        <div class="flex items-center gap-2">
            <!-- 페이지 표시 -->
            <div
                class="flex items-center border rounded-md h-8 px-3 gap-1 text-xs font-medium tabular-nums min-w-[4.5rem] justify-center"
            >
                <template v-if="totalPages > 0">
                    <span>{{ currentPage }}</span>
                    <span class="text-muted-foreground">/</span>
                    <span class="text-muted-foreground">{{ totalPages }}</span>
                </template>
                <template v-else>
                    <Skeleton class="h-3 w-4" />
                    <span class="text-muted-foreground">/</span>
                    <Skeleton class="h-3 w-4" />
                </template>
            </div>

            <Separator orientation="vertical" class="h-5" />

            <!-- 줌 컨트롤 -->
            <Tooltip>
                <TooltipTrigger as-child>
                    <Button
                        variant="ghost"
                        size="icon"
                        :disabled="!canZoomOut"
                        @click="$emit('zoomOut')"
                    >
                        <Minus class="size-4" />
                    </Button>
                </TooltipTrigger>
                <TooltipContent><p>축소</p></TooltipContent>
            </Tooltip>

            <Tooltip>
                <TooltipTrigger as-child>
                    <Button
                        variant="ghost"
                        size="icon"
                        :class="zoomMode === 'fit' ? 'bg-accent' : ''"
                        @click="$emit('fitToWidth')"
                    >
                        <ArrowLeftRight class="size-4" />
                    </Button>
                </TooltipTrigger>
                <TooltipContent><p>폭에 맞춤</p></TooltipContent>
            </Tooltip>

            <Tooltip>
                <TooltipTrigger as-child>
                    <Button
                        variant="ghost"
                        size="icon"
                        :disabled="!canZoomIn"
                        @click="$emit('zoomIn')"
                    >
                        <Plus class="size-4" />
                    </Button>
                </TooltipTrigger>
                <TooltipContent><p>확대</p></TooltipContent>
            </Tooltip>

            <Separator orientation="vertical" class="h-5" />

            <Tooltip>
                <TooltipTrigger as-child>
                    <Button variant="ghost" size="icon" @click="toggleDark()">
                        <Sun v-if="isDark" class="size-4" />
                        <Moon v-else class="size-4" />
                    </Button>
                </TooltipTrigger>
                <TooltipContent
                    ><p>{{ isDark ? "라이트 모드" : "다크 모드" }}</p></TooltipContent
                >
            </Tooltip>

            <Tooltip>
                <TooltipTrigger as-child>
                    <Button variant="ghost" size="icon" as-child>
                        <a :href="downloadUrl" :download="filename">
                            <Download class="size-4" />
                        </a>
                    </Button>
                </TooltipTrigger>
                <TooltipContent><p>다운로드</p></TooltipContent>
            </Tooltip>

            <Tooltip>
                <TooltipTrigger as-child>
                    <Button variant="ghost" size="icon" @click="$emit('toggleFullscreen')">
                        <Maximize2 v-if="!isFullscreen" class="size-4" />
                        <Minimize2 v-else class="size-4" />
                    </Button>
                </TooltipTrigger>
                <TooltipContent
                    ><p>{{ isFullscreen ? "전체화면 종료" : "전체화면" }}</p></TooltipContent
                >
            </Tooltip>
        </div>
    </header>
</template>
