package com.jarvisai.assistant.ide

import java.io.File

data class ProjectItem(
    val name: String,
    val path: String
)

data class FileNode(
    val file: File,
    val name: String = file.name,
    val isDirectory: Boolean = file.isDirectory,
    val path: String = file.absolutePath,
    var isExpanded: Boolean = false,
    var children: List<FileNode> = emptyList()
)

data class OpenTab(
    val path: String,
    val title: String,
    var content: String,
    var isDirty: Boolean = false
)

data class GitStatusResponse(
    val branch: String = "main",
    val stagedFiles: List<String> = emptyList(),
    val unstagedFiles: List<String> = emptyList(),
    val untrackedFiles: List<String> = emptyList()
)
