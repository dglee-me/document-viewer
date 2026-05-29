import { createRouter, createWebHistory } from "vue-router";
import DocumentListPage from "@/pages/DocumentListPage.vue";
import DocumentViewerPage from "@/pages/document/DocumentViewerPage.vue";

export default createRouter({
    history: createWebHistory(),
    routes: [
        { path: "/", component: DocumentListPage },
        { path: "/document/:id", component: DocumentViewerPage },
    ],
});
