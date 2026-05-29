export interface DocumentResponse {
    id: number;
    originalFilename: string;
    documentType: "PDF";
    fileSize: number;
    pageCount: number | null;
    createdAt: string;
}

export async function fetchDocuments(): Promise<DocumentResponse[]> {
    const res = await fetch("/api/documents");
    if (!res.ok) throw new Error("문서 목록을 불러올 수 없습니다.");
    return res.json();
}

export async function fetchDocument(id: number): Promise<DocumentResponse> {
    const res = await fetch(`/api/documents/${id}`);
    if (!res.ok) throw new Error("문서를 찾을 수 없습니다.");
    return res.json();
}

export async function uploadDocument(file: File): Promise<DocumentResponse> {
    const formData = new FormData();
    formData.append("file", file);
    const res = await fetch("/api/documents", { method: "POST", body: formData });
    if (!res.ok) throw new Error("파일 업로드에 실패했습니다.");
    return res.json();
}

export function getDocumentFileUrl(id: number): string {
    return `/api/documents/${id}/file`;
}
