package io.github.project.vault.core.root

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean
        get() = exitCode == 0

    val output: String
        get() = stdout.ifBlank { stderr }
}
