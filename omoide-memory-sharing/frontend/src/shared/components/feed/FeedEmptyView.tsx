export interface FeedEmptyViewProps {
    message?: string;
}

export function FeedEmptyView({ message = '表示できるおもいではまだありません' }: FeedEmptyViewProps) {
    return (
        <div className="flex flex-col items-center justify-center py-20 text-gray-400">
            <svg className="w-16 h-16 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            <p className="text-lg font-medium">{message}</p>
        </div>
    );
}
