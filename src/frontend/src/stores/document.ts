import { defineStore } from "pinia";
import { ref } from "vue";
import { fetchDocuments, uploadDocument, type DocumentResponse } from "@/api/document";

export const useDocumentStore = defineStore("document", () => {
    const documents = ref<DocumentResponse[]>([]);
    const loading = ref(false);
    const error = ref<string | null>(null);

    async function loadDocuments() {
        loading.value = true;
        error.value = null;
        try {
            documents.value = await fetchDocuments();
        } catch (e) {
            error.value = e instanceof Error ? e.message : "오류가 발생했습니다.";
        } finally {
            loading.value = false;
        }
    }

    async function upload(file: File): Promise<DocumentResponse> {
        const doc = await uploadDocument(file);
        documents.value.unshift(doc);
        return doc;
    }

    return { documents, loading, error, loadDocuments, upload };
});
