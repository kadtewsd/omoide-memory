import { useState } from 'react';
import { useComments } from '@/shared/hooks/useComments';
import { AlbumGrid, PhotobookEditor } from './components';
import { MemoryModal } from '@/shared/components/MemoryModal';
import { PrimaryButton } from '@/shared/components/button';
import { AlbumDetail } from '@/shared/types';
import { PHOTOBOOK_ABSOLUTE_MAX } from '@/shared/hooks/usePhotobookSelection';

// ── State モデリングについて ──────────────────────────────────────────────
// Kotlin の sealed class / ADT に相当するものを TS で表現する際、
// 「class + instanceof」は一見安全に見えるが TS では落とし穴がある。
//
//   class EditingAlbumState {
//       readonly value = "edit" as const   // ← 自己申告。型検査は "edit" を書いた
//       constructor(readonly albumDetail: AlbumDetail) {}  //   本人だけが守る
//   }
//
// → value フィールドが「そのクラスだけが持つ」という保証を型システムが強制しない。
//   また albumDetail と "edit" の対応はクラス定義の中だけで閉じており、
//   union 全体として「"edit" なら必ず albumDetail がある」という保証が型から見えない。
//
// 無名オブジェクトの Discriminated Union はこの問題を解決する。
//
//   type State = { value: "view" } | { value: "create" } | { value: "edit", albumDetail: AlbumDetail }
//
// → "edit" と albumDetail が型定義の中で必ず対になるため、
//   ラベルとペイロードのずれが型エラーとして検出される。
//   switch(state.value) で網羅チェックも効く。
//   class より簡潔で、冗長な constructor / readonly 宣言も不要。
// ────────────────────────────────────────────────────────────────────────────

type PhotobookListPageState = { value: "view" } | { value: "create" } | { value: "edit", albumDetail: AlbumDetail };


export function AlbumListPage() {
    const [pageState, setPageState] = useState<PhotobookListPageState>({ value: "view" });
    const { selectedItem, comments, commentsLoading, openModal, closeModal } = useComments();

    const toView = () => setPageState({ value: "view" })
    switch (pageState.value) {
        case "create":
            return (
                <PhotobookEditor
                    title="フォトブック用写真を選択"
                    previewTitle="フォトブック確認"
                    onComplete={toView}
                    onCancel={toView}
                />
            );

        case "edit":
            return (
                <PhotobookEditor
                    albumId={pageState.albumDetail.albumId}
                    title={`アルバム編集: ${pageState.albumDetail.albumName} `}
                    previewTitle={`アルバム確認: ${pageState.albumDetail.albumName} `}
                    initialPhotos={pageState.albumDetail.photos}
                    initialAlbumName={pageState.albumDetail.albumName}
                    initialMaxCount={
                        pageState.albumDetail.photos.length > 0
                            ? Math.max(pageState.albumDetail.photos.length, PHOTOBOOK_ABSOLUTE_MAX)
                            : PHOTOBOOK_ABSOLUTE_MAX
                    }
                    onComplete={toView}
                    onCancel={toView}
                />
            );
        case "view":
            return (
                <div className="min-h-screen bg-gray-50 text-gray-900">
                    <main className="p-4 sm:p-6 lg:p-8 space-y-6">
                        <div className="flex items-center justify-between">
                            <h1 className="text-xl font-bold text-gray-900">アルバム一覧</h1>
                            <PrimaryButton
                                onClick={() => setPageState({ value: "create" })}
                            >
                                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                                </svg>
                                <span>新規アルバム作成</span>
                            </PrimaryButton>
                        </div>
                        <AlbumGrid
                            onPhotoClick={openModal}
                            onEditAlbum={(albumDetail) => setPageState({ value: "edit", albumDetail: albumDetail })}
                        />
                    </main>
                    <MemoryModal
                        selectedItem={selectedItem}
                        comments={comments}
                        commentsLoading={commentsLoading}
                        onClose={closeModal}
                    />
                </div>
            );
    }
}
