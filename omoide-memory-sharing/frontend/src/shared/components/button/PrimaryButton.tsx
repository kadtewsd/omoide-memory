export interface PrimaryButtonProps {
    onClick?: (e: React.MouseEvent<HTMLButtonElement>) => void;
    type?: 'button' | 'submit';
    disabled?: boolean;
    children: React.ReactNode;
    className?: string;
}

export function PrimaryButton({
    onClick,
    type = 'button',
    disabled,
    children,
    className = '',
}: PrimaryButtonProps) {
    return (
        <button
            type={type}
            onClick={onClick}
            disabled={disabled}
            className={`px-5 py-2.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 active:bg-blue-800 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl shadow-sm transition-colors flex items-center justify-center gap-2 min-h-[44px] ${className}`}
        >
            {children}
        </button>
    );
}
