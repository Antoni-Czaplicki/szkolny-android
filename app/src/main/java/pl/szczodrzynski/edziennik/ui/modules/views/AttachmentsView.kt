/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-1.
 */

package pl.szczodrzynski.edziennik.ui.modules.views

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.util.AttributeSet
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.api.edziennik.EdziennikTask
import pl.szczodrzynski.edziennik.data.api.events.AttachmentGetEvent
import pl.szczodrzynski.edziennik.get
import pl.szczodrzynski.edziennik.isNotNullNorEmpty
import pl.szczodrzynski.edziennik.utils.SimpleDividerItemDecoration
import pl.szczodrzynski.edziennik.utils.Utils
import java.io.File

class AttachmentsView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {
    companion object {
        private const val TAG = "AttachmentsFragment"
        const val TYPE_MESSAGE = 0
        const val TYPE_EVENT = 1
    }

    private val storageDir by lazy {
        val storageDir = Environment.getExternalStoragePublicDirectory("Szkolny.eu")
        storageDir.mkdirs()
        storageDir
    }

    fun init(arguments: Bundle, owner: Any) {
        val list = this as? RecyclerView ?: return

        val profileId = arguments.get<Int>("profileId") ?: return
        val attachmentIds = arguments.getLongArray("attachmentIds") ?: return
        val attachmentNames = arguments.getStringArray("attachmentNames") ?: return
        val attachmentSizes = arguments.getLongArray("attachmentSizes")

        val adapter = AttachmentAdapter(context, onAttachmentClick = { item ->
            downloadAttachment(item)
        }, onAttachmentLongClick = { chip, item ->
            val popupMenu = PopupMenu(chip.context, chip)
            popupMenu.menu.add(0, 1, 0, R.string.messages_attachment_download_again)
            popupMenu.setOnMenuItemClickListener {
                downloadAttachment(item, forceDownload = true)
                true
            }
            popupMenu.show()
        })

        attachmentIds.forEachIndexed { index, id ->
            val name = attachmentNames[index] ?: return@forEachIndexed
            val size = attachmentSizes?.getOrNull(index)

            val item = AttachmentAdapter.Item(profileId, owner, id, name, size)
            adapter.items += item
            checkAttachment(item = item)
        }

        // load & configure the adapter
        if (adapter.items.isNotNullNorEmpty() && list.adapter == null) {
            list.adapter = adapter
            list.apply {
                setHasFixedSize(false)
                layoutManager = LinearLayoutManager(context)
                addItemDecoration(SimpleDividerItemDecoration(context))
            }
        }
    }

    private fun checkAttachment(item: AttachmentAdapter.Item): Boolean {
        val attachmentDataFile = File(storageDir, "." + item.profileId + "_" + item.ownerId + "_" + item.id)
        item.isDownloaded = if (attachmentDataFile.exists()) {
            try {
                val attachmentFileName = Utils.getStringFromFile(attachmentDataFile)
                val attachmentFile = File(attachmentFileName)
                attachmentFile.exists()
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        } else false
        return item.isDownloaded
    }

    private fun downloadAttachment(attachment: AttachmentAdapter.Item, forceDownload: Boolean = false) {
        if (!forceDownload && attachment.isDownloaded) {
            Utils.openFile(context, File(Utils.getStorageDir(), attachment.name))
            return
        }

        attachment.isDownloading = true
        (adapter as? AttachmentAdapter)?.let {
            it.notifyItemChanged(it.items.indexOf(attachment))
        }

        EdziennikTask.attachmentGet(
                attachment.profileId,
                attachment.owner,
                attachment.id,
                attachment.name
        ).enqueue(context)
    }

    private val lastUpdate: Long = 0
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onAttachmentGetEvent(event: AttachmentGetEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        val attachment = (adapter as? AttachmentAdapter)?.items?.firstOrNull {
            it.profileId == event.profileId
                && it.owner == event.owner
                && it.id == event.attachmentId
        } ?: return


        when (event.eventType) {
            AttachmentGetEvent.TYPE_FINISHED -> {
                // save the downloaded file name
                attachment.downloadedName = event.fileName
                attachment.isDownloading = false
                attachment.isDownloaded = true

                // update file name for iDziennik which
                // does not provide the name before downloading
                if (!attachment.name.contains("."))
                    attachment.name = File(attachment.downloadedName).name

                // open the file
                Utils.openFile(context, File(Utils.getStorageDir(), attachment.name))
            }

            AttachmentGetEvent.TYPE_PROGRESS -> {
                attachment.downloadProgress = event.bytesWritten.toFloat() / 1000000f
            }
        }

        if (event.eventType != AttachmentGetEvent.TYPE_PROGRESS || System.currentTimeMillis() - lastUpdate > 100L) {
            (adapter as? AttachmentAdapter)?.let {
                it.notifyItemChanged(it.items.indexOf(attachment))
            }
        }
    }

    override fun onAttachedToWindow() {
        EventBus.getDefault().register(this)
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        EventBus.getDefault().unregister(this)
    }
}
