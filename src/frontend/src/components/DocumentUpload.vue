<script setup lang="ts">
import { ref } from "vue";

const emit = defineEmits<{
    upload: [file: File];
}>();

const ACCEPTED_FILE_TYPES = ".pdf";

const isDragging = ref(false);
const fileInput = ref<HTMLInputElement>();

function onDrop(e: DragEvent) {
    isDragging.value = false;
    const file = e.dataTransfer?.files[0];
    if (file) emit("upload", file);
}

function onFileChange(e: Event) {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (file) emit("upload", file);
}
</script>

<template>
    <div
        class="border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors"
        :class="
            isDragging ? 'border-primary bg-primary/5' : 'border-border hover:border-primary/50'
        "
        @dragover.prevent="isDragging = true"
        @dragleave="isDragging = false"
        @drop.prevent="onDrop"
        @click="fileInput?.click()"
    >
        <input
            ref="fileInput"
            type="file"
            :accept="ACCEPTED_FILE_TYPES"
            class="hidden"
            @change="onFileChange"
        />
        <p class="text-sm text-muted-foreground">
            PDF 파일을 드래그하거나 <span class="text-primary font-medium">클릭</span>하여 업로드
        </p>
    </div>
</template>
