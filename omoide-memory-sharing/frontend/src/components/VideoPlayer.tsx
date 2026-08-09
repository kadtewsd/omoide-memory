import { getVideoStreamUrl } from '../api';

interface VideoPlayerProps {
    videoId: string;
    poster?: string | null;
    className?: string;
}

export function VideoPlayer({ videoId, poster, className = "max-w-full max-h-full object-contain rounded-lg" }: VideoPlayerProps) {
    return (
        <video
            controls
            autoPlay
            crossOrigin="anonymous"
            className={className}
            src={getVideoStreamUrl(videoId)}
            poster={poster || undefined}
        >
            動画を再生できません。
        </video>
    );
}
