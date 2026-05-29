<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useDocumentStore } from "@/stores/document";
import DocumentUpload from "@/components/DocumentUpload.vue";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
    Table,
    TableBody,
    TableCell,
    TableEmpty,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import { Moon, Sun } from "@lucide/vue";
import { useColorMode } from "@/composables/useColorMode";

const LOCALE = "ko-KR";
const BYTES_PER_KB = 1024;
const BYTES_PER_MB = BYTES_PER_KB * 1024;
const DATE_FORMAT_OPTIONS: Intl.DateTimeFormatOptions = {
    year: "numeric",
    month: "long",
    day: "numeric",
};

const { isDark, toggleDark } = useColorMode();

const store = useDocumentStore();
const router = useRouter();
const uploading = ref(false);
const uploadError = ref<string | null>(null);

onMounted(() => store.loadDocuments());

async function handleUpload(file: File) {
    uploading.value = true;
    uploadError.value = null;
    try {
        await store.upload(file);
    } catch (e) {
        uploadError.value = e instanceof Error ? e.message : "업로드 실패";
    } finally {
        uploading.value = false;
    }
}

function formatFileSize(bytes: number): string {
    if (bytes < BYTES_PER_MB) return `${(bytes / BYTES_PER_KB).toFixed(1)} KB`;
    return `${(bytes / BYTES_PER_MB).toFixed(1)} MB`;
}

function formatPageCount(pageCount: number | null): string {
    return pageCount === null ? "페이지 수 없음" : `${pageCount.toLocaleString(LOCALE)}p`;
}

function formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString(LOCALE, DATE_FORMAT_OPTIONS);
}

const SKELETON_ROWS = [
    { type: "w-12", name: "w-64" },
    { type: "w-14", name: "w-40" },
    { type: "w-10", name: "w-56" },
    { type: "w-12", name: "w-48" },
];
</script>

<template>
    <div class="max-w-3xl mx-auto px-4 py-8">
        <div class="flex items-center justify-between mb-6">
            <h1 class="text-2xl font-semibold">문서 뷰어</h1>
            <Button variant="ghost" size="icon" @click="toggleDark()">
                <Sun v-if="isDark" class="size-4" />
                <Moon v-else class="size-4" />
            </Button>
        </div>

        <DocumentUpload class="mb-4" @upload="handleUpload" />

        <p v-if="uploading" class="text-sm text-muted-foreground mb-4">업로드 중...</p>
        <p v-if="uploadError" class="text-sm text-destructive mb-4">{{ uploadError }}</p>

        <Table class="mt-6 table-fixed">
            <TableHeader>
                <TableRow>
                    <TableHead class="w-20">형식</TableHead>
                    <TableHead>파일명</TableHead>
                    <TableHead class="w-24 text-right">페이지</TableHead>
                    <TableHead class="w-24 text-right">크기</TableHead>
                    <TableHead class="w-32 text-right">업로드일</TableHead>
                </TableRow>
            </TableHeader>
            <TableBody>
                <template v-if="store.loading">
                    <TableRow v-for="(row, i) in SKELETON_ROWS" :key="i">
                        <TableCell>
                            <Skeleton class="h-4" :class="row.type" />
                        </TableCell>
                        <TableCell>
                            <Skeleton class="h-4" :class="row.name" />
                        </TableCell>
                        <TableCell class="text-right">
                            <Skeleton class="h-4 w-8 ml-auto" />
                        </TableCell>
                        <TableCell class="text-right">
                            <Skeleton class="h-4 w-14 ml-auto" />
                        </TableCell>
                        <TableCell class="text-right">
                            <Skeleton class="h-4 w-24 ml-auto" />
                        </TableCell>
                    </TableRow>
                </template>
                <template v-else-if="store.error">
                    <TableEmpty :colspan="5">
                        <span class="text-destructive">{{ store.error }}</span>
                    </TableEmpty>
                </template>
                <template v-else-if="store.documents.length === 0">
                    <TableEmpty :colspan="5">업로드된 문서가 없습니다.</TableEmpty>
                </template>
                <template v-else>
                    <TableRow
                        v-for="doc in store.documents"
                        :key="doc.id"
                        class="cursor-pointer"
                        @click="router.push(`/document/${doc.id}`)"
                    >
                        <TableCell>
                            <Badge variant="secondary">{{ doc.documentType }}</Badge>
                        </TableCell>
                        <TableCell class="font-medium truncate max-w-0">{{
                            doc.originalFilename
                        }}</TableCell>
                        <TableCell class="text-right text-muted-foreground">{{
                            formatPageCount(doc.pageCount)
                        }}</TableCell>
                        <TableCell class="text-right text-muted-foreground">{{
                            formatFileSize(doc.fileSize)
                        }}</TableCell>
                        <TableCell class="text-right text-muted-foreground">{{
                            formatDate(doc.createdAt)
                        }}</TableCell>
                    </TableRow>
                </template>
            </TableBody>
        </Table>
    </div>
</template>
