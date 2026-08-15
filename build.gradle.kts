// Top-level build file where you can add configuration options common to all sub-projects/modules.

// OneDrive puede bloquear app/build e impedir que Gradle limpie archivos generados.
// Usamos por defecto una carpeta no sincronizada en el mismo disco (KSP requiere
// que las fuentes y los archivos generados estén en la misma unidad en Windows).
val projectDrive = rootProject.projectDir.toPath().root.toString().replace('\\', '/')
val safeProjectName = rootProject.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
val isWindowsHost = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
val defaultBuildRoot = if (isWindowsHost) {
    "${projectDrive}AndroidStudioBuilds/$safeProjectName"
} else {
    rootProject.layout.projectDirectory.dir("build").asFile.absolutePath
}
val externalBuildRoot = providers.gradleProperty("externalBuildDir")
    .orElse(defaultBuildRoot)
    .get()

subprojects {
    layout.buildDirectory.set(file("$externalBuildRoot/$name"))
}
