package cock.crest.purrfectsnap.lite.task

import cock.crest.purrfectsnap.lite.RemoteSideContext
import cock.crest.purrfectsnap.lite.bridge.task.TaskInterface
import cock.crest.purrfectsnap.lite.bridge.task.TaskListener

class RemoteTaskInterface(
    private val context: RemoteSideContext
) : TaskInterface.Stub() {
    private val activeTasks = context.taskManager.getActiveTasks()

    override fun createTask(type: String, title: String, author: String, hash: String): String {
        val taskType = TaskType.fromKey(type)
        val task = Task(
            type = taskType,
            title = title,
            author = author.takeIf { it.isNotBlank() },
            hash = hash,
            isAutoOpen = taskType == TaskType.CHAT_ACTION
        )
        context.taskManager.createPendingTask(task)
        return hash
    }

    override fun updateTaskProgress(hash: String, label: String, progress: Int) {
        activeTasks.values.find { it.task.hash == hash }?.updateProgress(label, progress)
            ?: context.taskManager.getTaskByHash(hash)?.let {
                if (!it.status.isFinalStage()) {
                    it.status = TaskStatus.RUNNING
                    it.extra = label
                }
            }
    }

    override fun cancelTask(hash: String) {
        activeTasks.values.find { it.task.hash == hash }?.cancel() ?: context.taskManager.getTaskByHash(hash)?.let {
            it.status = TaskStatus.CANCELLED
        }
    }

    override fun failTask(hash: String, reason: String) {
        activeTasks.values.find { it.task.hash == hash }?.fail(reason) ?: context.taskManager.getTaskByHash(hash)?.let {
            it.status = TaskStatus.FAILURE
            it.extra = reason
        }
    }

    override fun successTask(hash: String) {
        activeTasks.values.find { it.task.hash == hash }?.success() ?: context.taskManager.getTaskByHash(hash)?.let {
            it.status = TaskStatus.SUCCESS
        }
    }

    override fun registerTaskListener(hash: String, listener: TaskListener) {
        activeTasks.values.find { it.task.hash == hash }?.addListener(
            PendingTaskListener(
                onSuccess = { listener.onSuccess() },
                onCancel = { listener.onCancel() },
                onProgress = { label, progress -> listener.onProgress(label ?: "", progress) },
                onStateChange = { status -> listener.onStateChange(status.key) }
            )
        )
    }

    override fun unregisterTaskListener(hash: String, listener: TaskListener) {
        activeTasks.values.find { it.task.hash == hash }?.removeListener(PendingTaskListener())
    }
}
