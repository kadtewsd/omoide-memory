export interface SecondaryButtonProps {
    onClick?: (e: React.MouseEvent<HTMLButtonElement>) => void;
    type?: 'button' | 'submit';
    disabled?: boolean;
    children: React.ReactNode;
    className?: string;
}

export function SecondaryButton({
    onClick,
    type = 'button',
    disabled,
    children,
    className = '',
}: SecondaryButtonProps) {
    return (
        <button
            type={type}
            onClick={onClick}
            disabled={disabled}
            className={`px-5 py-2.5 text-sm font-semibold text-gray-700 bg-gray-100 hover:bg-gray-200 active:bg-gray-300 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl transition-colors flex items-center justify-center gap-2 min-h-[44px] ${className}`}
        >
            {children}
        </button>
    );
}
