package dev.ryazha.sassist.data

import org.junit.Assert.assertTrue
import org.junit.Test

class MediaBackupMappingTest {
    @Test
    fun ownConfirmedAttachmentKeepsItsLocalRecoveryMarker() {
        val local = LocalMessage(
            id = "m_1",
            channel = "general",
            username = "Dimasick-git",
            text = "",
            ts = 1L,
            mediaJson = """{"id":"md_1","kind":"image","mime":"image/jpeg","name":"photo.jpg","size":123}""",
            localMediaUri = "content://media/external/images/media/42"
        )

        val message = local.toChatMessage()

        assertTrue(message.hasLocalMediaBackup)
    }

    @Test
    fun remoteAttachmentDoesNotExposeRecoveryAction() {
        val remote = LocalMessage(
            id = "m_2",
            channel = "general",
            username = "other-user",
            text = "",
            ts = 1L,
            mediaJson = """{"id":"md_2","kind":"file","mime":"application/pdf","name":"guide.pdf","size":456}"""
        )

        assertTrue(!remote.toChatMessage().hasLocalMediaBackup)
    }
}
