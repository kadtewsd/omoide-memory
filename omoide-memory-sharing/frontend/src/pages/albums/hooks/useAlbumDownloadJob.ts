import { useState, useCallback } from 'react';
import {
    startAlbumDownloadJob,
    getAlbumDownloadJobEventsUrl,
    downloadJobFile,
} from '@/shared/api';

export type DownloadStatus = 'IDLE' | 'STARTING_JOB' | 'PROCESSING' | 'COMPLETED' | 'ERROR';

export interface StartDownloadParams {
    albumId: string;
    onProgress?: (percentage: number) => void;
}

export interface UseAlbumDownloadJobResult {
    status: DownloadStatus;
    percentage: number;
    processed: number;
    total: number;
    errorMessage: string | null;
    startDownload: (params: StartDownloadParams) => Promise<void>;
    reset: () => void;
}

/**
 * アルバムの非同期 ZIP ダウンロードジョブを管理するカスタムフック。
 * 1. ジョブ開始要求 (202 Accepted)
 * 2. EventSource による SSE 進捗ストリーム購読
 * 3. 完了イベント受信時にファイルを自動ダウンロードしてブラウザで保存
 */
export function useAlbumDownloadJob(): UseAlbumDownloadJobResult {
    const [status, setStatus] = useState<DownloadStatus>('IDLE');
    const [percentage, setPercentage] = useState<number>(0);
    const [processed, setProcessed] = useState<number>(0);
    const [total, setTotal] = useState<number>(0);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    const reset = useCallback(() => {
        setStatus('IDLE');
        setPercentage(0);
        setProcessed(0);
        setTotal(0);
        setErrorMessage(null);
    }, []);

    const triggerBrowserDownload = (blob: Blob, fileName: string) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
    };

    const startDownload = useCallback(async ({ albumId, onProgress }: StartDownloadParams): Promise<void> => {
        setStatus('STARTING_JOB');
        setPercentage(0);
        setProcessed(0);
        setTotal(0);
        setErrorMessage(null);

        try {
            const { jobId } = await startAlbumDownloadJob(albumId);
            setStatus('PROCESSING');
            onProgress?.(0);

            await new Promise<void>((resolve, reject) => {
                const eventSource = new EventSource(getAlbumDownloadJobEventsUrl(jobId));

                eventSource.addEventListener('progress', (e: MessageEvent) => {
                    try {
                        const data = JSON.parse(e.data);
                        const progressPercentage = data.percentage || 0;
                        setProcessed(data.processed || 0);
                        setTotal(data.total || 0);
                        setPercentage(progressPercentage);
                        onProgress?.(progressPercentage);
                    } catch {
                        // パース失敗時は無視
                    }
                });

                eventSource.addEventListener('completed', async (e: MessageEvent) => {
                    eventSource.close();
                    try {
                        const data = JSON.parse(e.data);
                        const fallbackFileName = data.fileName || 'album.zip';
                        const { blob, fileName } = await downloadJobFile(jobId);
                        triggerBrowserDownload(blob, fileName || fallbackFileName);
                        setStatus('COMPLETED');
                        setPercentage(100);
                        resolve();
                    } catch (err) {
                        const message = err instanceof Error ? err.message : 'ZIP ファイルの取得に失敗しました';
                        setErrorMessage(message);
                        setStatus('ERROR');
                        reject(err);
                    }
                });

                eventSource.onerror = () => {
                    eventSource.close();
                    const message = 'ダウンロード進捗の受信中にエラーが発生しました';
                    setErrorMessage(message);
                    setStatus('ERROR');
                    reject(new Error(message));
                };
            });
        } catch (err) {
            const message = err instanceof Error ? err.message : 'ダウンロードジョブの開始に失敗しました';
            setErrorMessage(message);
            setStatus('ERROR');
            throw err;
        }
    }, []);

    return {
        status,
        percentage,
        processed,
        total,
        errorMessage,
        startDownload,
        reset,
    };
}
