import { useRef, useEffect } from 'react';

interface Props {
    onLoadMore: () => void;
    hasMore: boolean;
    loading: boolean;
}

export function InfiniteScrollLoader({ onLoadMore, hasMore, loading }: Props) {
    const loaderRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const observer = new IntersectionObserver(
            (entries) => {
                if (entries[0].isIntersecting && hasMore && !loading) {
                    onLoadMore();
                }
            },
            { 
                threshold: 0.1, // Trigger slightly earlier for better UX
                rootMargin: '100px' // Start loading before reaching the very bottom
            }
        );

        const currentLoader = loaderRef.current;
        if (currentLoader) {
            observer.observe(currentLoader);
        }

        return () => {
            if (currentLoader) {
                observer.unobserve(currentLoader);
            }
        };
    }, [hasMore, loading, onLoadMore]);

    if (!hasMore) return null;

    return (
        <div className="flex justify-center mt-8 pb-8" ref={loaderRef}>
            {loading && (
                <div className="flex items-center gap-2 text-gray-500 text-sm font-medium">
                    <svg className="animate-spin h-5 w-5 text-blue-600" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                    </svg>
                    読み込み中...
                </div>
            )}
        </div>
    );
}
