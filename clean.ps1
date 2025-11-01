Write-Host "Cleaning Manjano build/cache folders..." -ForegroundColor Cyan

$foldersToDelete = @(
    ".gradle",
    "build",
    "app\build",
    ".idea",
    ".caches"
)

$filesToDelete = @(
    "local.properties"
)

foreach ($folder in $foldersToDelete) {
    if (Test-Path $folder) {
        Remove-Item -Recurse -Force $folder
        Write-Host "Deleted folder: $folder"
    } else {
        Write-Host "Not found (already deleted): $folder"
    }
}

foreach ($file in $filesToDelete) {
    if (Test-Path $file) {
        Remove-Item -Force $file
        Write-Host "Deleted file: $file"
    } else {
        Write-Host "Not found (already deleted): $file"
    }
}

Write-Host "`nDone cleaning Manjano project build artifacts." -ForegroundColor Green
