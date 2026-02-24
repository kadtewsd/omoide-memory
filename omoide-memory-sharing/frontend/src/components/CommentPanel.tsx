import { Comment } from '../types';

interface Props {
    comments: Comment[];
    loading: boolean;
}

export function CommentPanel({ comments, loading }: Props) {
    return (
        <aside className="w-80 bg-white shadow-2xl flex flex-col animate-in slide-in-from-right duration-300">
            <header className="p-4 border-b border-gray-100">
                <h2 className="text-lg font-medium text-gray-900">コメント</h2>
            </header>

            <div className="flex-1 overflow-y-auto p-4 space-y-6">
                {loading ? (
                    <div className="flex justify-center py-8">
                        <div className="w-6 h-6 border-2 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
                    </div>
                ) : comments.length === 0 ? (
                    <div className="text-center py-8 text-gray-400 text-sm">
                        まだコメントはありません
                    </div>
                ) : (
                    comments.map(comment => (
                        <div key={comment.id} className="flex gap-3">
                            <div className="w-8 h-8 rounded-full bg-gray-200 flex-shrink-0 overflow-hidden">
                                {comment.commenterIconBase64 ? (
                                    <img src={`data:image/jpeg;base64,${comment.commenterIconBase64}`} alt="" className="w-full h-full object-cover" />
                                ) : (
                                    <div className="w-full h-full flex items-center justify-center text-gray-500 font-medium text-sm">
                                        {comment.commenterName.charAt(0)}
                                    </div>
                                )}
                            </div>
                            <div>
                                <div className="flex items-baseline gap-2">
                                    <span className="font-medium text-sm text-gray-900">{comment.commenterName}</span>
                                    <span className="text-xs text-gray-500">
                                        {new Date(comment.commentedAt).toLocaleDateString()}
                                    </span>
                                </div>
                                <p className="text-sm text-gray-700 mt-1 whitespace-pre-wrap leading-relaxed">
                                    {comment.commentBody}
                                </p>
                            </div>
                        </div>
                    ))
                )}
            </div>
        </aside>
    );
}
