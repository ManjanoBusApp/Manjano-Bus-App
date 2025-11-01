# Clean Gradle, delete build folders, and restart Android Studio
# Run this when builds act strange but Gradle cache seems fine.
# If issues persist, do a manual "Invalidate Caches & Restart" from Android Studio.

# Step 1: Run gradlew clean
Write-Host "Running gradlew clean..."
./gradlew clean

# Step 2: Delete build folders
Write-Host "Deleting build folders..."
Get-ChildItem -Path . -Recurse -Force -Directory -Include build | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

# Step 3: Restart Android Studio
Write-Host "Restarting Android Studio..."
Start-Process "C:\Users\renee\Android Studio\bin\studio64.exe"

# Reminder: If problems still remain after this,
# use "File > Invalidate Caches & Restart" inside Android Studio manually.
