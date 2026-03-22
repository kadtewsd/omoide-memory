export function ContentNotFound() {
    return (
        <div className="w-full h-full flex items-center justify-center bg-gray-200">
            <img 
                src="/content-not-found.jpg" 
                alt="Content not found" 
                className="w-full h-full object-cover opacity-60"
            />
            <div className="absolute inset-0 flex items-center justify-center">
                <span className="bg-black/40 text-white text-xs px-2 py-1 rounded backdrop-blur-sm">
                    No Preview
                </span>
            </div>
        </div>
    );
}
