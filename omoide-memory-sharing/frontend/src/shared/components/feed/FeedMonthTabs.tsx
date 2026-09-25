import { formatYearMonthDisplay } from '@/shared/hooks/useFeed';

export interface FeedMonthTabsProps {
    monthTabs: string[];
    selectedYearMonth?: string;
    onSelectMonthTab: (ym: string) => void;
}

export function FeedMonthTabs({ monthTabs, selectedYearMonth, onSelectMonthTab }: FeedMonthTabsProps) {
    if (monthTabs.length === 0) return null;

    return (
        <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar -mx-4 px-4 sm:mx-0 sm:px-0">
            <div className="flex items-center gap-2 py-0.5">
                {monthTabs.map(ym => {
                    const isSelected = ym === selectedYearMonth;
                    return (
                        <button
                            key={ym}
                            type="button"
                            onClick={() => onSelectMonthTab(ym)}
                            className={`px-4 py-2 text-xs sm:text-sm font-bold rounded-full whitespace-nowrap transition-colors min-h-[40px] flex items-center justify-center ${
                                isSelected
                                    ? 'bg-blue-600 text-white shadow-sm ring-2 ring-blue-600/30'
                                    : 'bg-gray-100 text-gray-800 hover:bg-gray-200 border border-gray-200'
                            }`}
                        >
                            {formatYearMonthDisplay(ym)}
                        </button>
                    );
                })}
            </div>
        </div>
    );
}
