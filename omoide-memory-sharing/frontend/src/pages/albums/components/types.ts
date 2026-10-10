import { MemoryFeedItem } from '@/shared/types';

export type PhotobookState =
    | { value: 'selecting' }
    | { value: 'previewing' }
    | { value: 'confirming'; message: string }
    | { value: 'replace-confirming'; oldPhoto: MemoryFeedItem; newPhoto: MemoryFeedItem };

export type AlbumDetailState =
    | { value: 'view' }
    | { value: 'delete'; deleteTargetIds: Set<string> };
