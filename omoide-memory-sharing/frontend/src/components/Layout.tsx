import { NavLink } from 'react-router-dom';

interface Props {
    children: React.ReactNode;
}

export function Layout({ children }: Props) {
    const navLinkClass = ({ isActive }: { isActive: boolean }) =>
        `px-3.5 py-1.5 text-xs sm:text-sm font-bold rounded-lg transition-colors min-h-[36px] flex items-center justify-center ${
            isActive
                ? 'bg-white text-gray-900 shadow-sm border border-gray-200'
                : 'text-gray-700 hover:text-gray-900'
        }`;

    return (
        <div className="min-h-screen bg-gray-50 text-gray-900">
            <header className="sticky top-0 z-30 bg-white/95 backdrop-blur-md shadow-sm border-b border-gray-200 px-4 sm:px-6 py-3.5">
                <div className="flex items-center justify-between gap-3">
                    <h1 className="text-lg sm:text-xl font-bold tracking-wide text-gray-900">
                        思い出のシェア
                    </h1>

                    <div className="inline-flex rounded-xl border border-gray-300 bg-gray-100 p-1 min-h-[44px]">
                        <NavLink to="/" end className={navLinkClass}>
                            すべて
                        </NavLink>
                        <NavLink to="/comment" className={navLinkClass}>
                            コメントのみ
                        </NavLink>
                        <NavLink to="/albums" className={navLinkClass}>
                            アルバム
                        </NavLink>
                        <NavLink to="/photobook" className={navLinkClass}>
                            フォトブック
                        </NavLink>
                    </div>
                </div>
            </header>

            {children}
        </div>
    );
}
