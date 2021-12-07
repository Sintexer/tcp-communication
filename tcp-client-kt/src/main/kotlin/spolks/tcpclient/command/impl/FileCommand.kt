package spolks.tcpclient.command.impl

import spolks.tcpclient.RESOURCES_FOLDER
import spolks.tcpclient.command.Command
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

abstract class FileCommand : Command {
    fun getFile(filename: String): File? {
        resourcesFolderExists()
        return File("$RESOURCES_FOLDER/$filename")
    }

    fun createFile(filename: String): File {
        resourcesFolderExists()
        val file = File("$RESOURCES_FOLDER/$filename")
        if (file.delete()) {
            file.createNewFile()
        }
        return file
    }

    private fun resourcesFolderExists() {
        if (!Files.exists(Path.of(RESOURCES_FOLDER))) {
            Files.createDirectory(Path.of(RESOURCES_FOLDER))
        }
    }
}
