import { AlbumContentItem } from '@/shared/types';

export type PhotobookState =
    | { value: 'selecting' }
    | { value: 'previewing' }
    | { value: 'confirming'; message: string }
    | { value: 'replace-confirming'; oldPhoto: AlbumContentItem; newPhoto: AlbumContentItem };

export type AlbumDetailState =
    | { value: 'view' }
    | { value: 'delete'; deleteTargetIds: Set<string> };
