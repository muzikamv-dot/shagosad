@echo off
rem Build ShagoSad APK on PC.
rem   build.cmd            - just build
rem   build.cmd -Install   - build and install to phone
rem   build.cmd -FreshRes  - recompile all 3672 resources
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1" %*
