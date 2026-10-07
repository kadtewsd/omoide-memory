export type PhotobookState =
    | { value: 'selecting' }
    | { value: 'previewing' }
    | { value: 'confirming'; message: string };

export type AlbumDetailState =
    | { value: 'view' }
    | { value: 'delete'; deleteTargetIds: Set<string> };
