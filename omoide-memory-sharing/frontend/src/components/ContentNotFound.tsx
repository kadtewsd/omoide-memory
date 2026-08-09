export function ContentNotFound() {
    return (
        <div className="w-full h-full flex items-center justify-center bg-gray-200">
            <img 
                src="/content-not-found.jpg" 
                alt="Content not found" 
                className="w-full h-full object-cover opacity-60"
            />
            <div className="absolute inset-0 flex items-center justify-center">
                <span className="bg-black/50 text-white text-xs px-2.5 py-1 rounded-md backdrop-blur-sm font-medium">
                    写真がありません
                </span>
            </div>
        </div>
    );
}
