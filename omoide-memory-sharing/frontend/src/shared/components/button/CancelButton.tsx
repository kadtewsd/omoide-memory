export interface CancelButtonProps {
    onClick: () => void;
    disabled?: boolean;
    className?: string;
}

export function CancelButton({ onClick, disabled, className = '' }: CancelButtonProps) {
    return (
        <button
            type="button"
            onClick={onClick}
            disabled={disabled}
            className={`px-5 py-2.5 text-sm font-semibold text-gray-700 bg-gray-100 hover:bg-gray-200 active:bg-gray-300 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl transition-colors min-h-[44px] flex items-center justify-center ${className}`}
        >
            キャンセル
        </button>
    );
}
