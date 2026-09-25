package com.kasakaid.omoidememory.service.query.album

import com.kasakaid.omoidememory.adapter.DownloadCompletedEvent
import com.kasakaid.omoidememory.adapter.DownloadProgressEvent
import com.kasakaid.omoidememory.shared.adapter.NotFoundException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 非同期ダウンロードジョブの管理クラス。
 * Spring WebFlux の [Sinks.Many] を利用して SSE による進捗・完了通知をリアクティブに配信し、
 * 生成された ZIP ファイルのバイナリをキャッシュします。
 */
@Component
class AlbumDownloadJobManager(
    private val albumDownloadQueryService: AlbumDownloadQueryService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val jobSinks = ConcurrentHashMap<UUID, Sinks.Many<ServerSentEvent<Any>>>()
    private val completedFiles = ConcurrentHashMap<UUID, AlbumZipResult>()

    /**
     * 指定された [albumId] に対する非同期ダウンロードジョブを開始し、生成された [jobId] を返します。
     *
     * @param albumId ダウンロード対象のアルバムID
     * @return 発行されたジョブID
     */
    fun startJob(albumId: UUID): UUID {
        val jobId = UUID.randomUUID()
        val sink = Sinks.many().replay().latest<ServerSentEvent<Any>>()
        jobSinks[jobId] = sink

        scope.launch {
            try {
                val result =
                    albumDownloadQueryService.createAlbumZip(
                        albumId = albumId,
                        onProgress = { processed, total ->
                            val percentage = if (total > 0) (processed * 100) / total else 100
                            val progressEvent =
                                DownloadProgressEvent(
                                    jobId = jobId,
                                    processed = processed,
                                    total = total,
                                    percentage = percentage,
                                )
                            sink.tryEmitNext(
                                ServerSentEvent
                                    .builder<Any>()
                                    .event("progress")
                                    .data(progressEvent)
                                    .build(),
                            )
                        },
                    )

                completedFiles[jobId] = result

                val completedEvent =
                    DownloadCompletedEvent(
                        jobId = jobId,
                        downloadUrl = "/albums/download-jobs/$jobId/file",
                        fileName = "${result.albumName}.zip",
                    )
                sink.tryEmitNext(
                    ServerSentEvent
                        .builder<Any>()
                        .event("completed")
                        .data(completedEvent)
                        .build(),
                )
                sink.tryEmitComplete()
            } catch (e: Exception) {
                sink.tryEmitError(e)
            }
        }

        return jobId
    }

    /**
     * 指定された [jobId] の SSE イベントストリームを取得します。
     *
     * @param jobId 購読対象のジョブID
     * @return [Flux] 形式のイベントストリーム
     */
    fun getJobEvents(jobId: UUID): Flux<ServerSentEvent<Any>> {
        val sink = jobSinks[jobId] ?: throw NotFoundException("Job not found with id: $jobId")
        return sink.asFlux()
    }

    /**
     * 指定された [jobId] で生成された ZIP ファイル結果を取得します。
     *
     * @param jobId 取得対象のジョブID
     * @return [AlbumZipResult]
     */
    fun getJobFile(jobId: UUID): AlbumZipResult = completedFiles[jobId] ?: throw NotFoundException("Completed file not found for job id: $jobId")
}
