package io.github.aristidisg.ensemble.data.files

import java.io.File

/**
 * Image layout (SPEC 3 step 9): filesDir/items/{id}/original.jpg, cutout.webp, thumb.webp and
 * filesDir/looks/{id}/. Paths stored on items are relative to filesDir so backups stay portable.
 */
class ImageStore(private val filesDir: File) {
    fun itemDir(id: String): File = File(filesDir, "items/$id").apply { mkdirs() }
    fun lookDir(id: String): File = File(filesDir, "looks/$id").apply { mkdirs() }

    fun original(id: String) = File(itemDir(id), "original.jpg")
    fun cutout(id: String) = File(itemDir(id), "cutout.webp")
    fun thumb(id: String) = File(itemDir(id), "thumb.webp")
    fun lookPhoto(id: String) = File(lookDir(id), "photo.jpg")

    fun relative(f: File): String = f.relativeTo(filesDir).path

    /** Resolves a stored relative path; null when missing. */
    fun resolve(path: String?): File? = path?.let { File(filesDir, it) }?.takeIf { it.exists() }

    fun deleteItem(id: String) { File(filesDir, "items/$id").deleteRecursively() }
    fun deleteLook(id: String) { File(filesDir, "looks/$id").deleteRecursively() }

    /** Copies an item's images to a new item (duplicate as new color). */
    fun copyItem(from: String, to: String) {
        val src = File(filesDir, "items/$from")
        if (src.exists()) src.copyRecursively(File(filesDir, "items/$to"), overwrite = true)
    }

    fun allImageFiles(): List<File> = listOf("items", "looks").flatMap { d ->
        File(filesDir, d).walkTopDown().filter { it.isFile }.toList()
    }

    val root: File get() = filesDir

    fun clear() {
        File(filesDir, "items").deleteRecursively()
        File(filesDir, "looks").deleteRecursively()
    }
}
