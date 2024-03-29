# vim: ft=sh

if [ "$(uname)" = Linux ]; then
    TAILWIND_OS=linux
else
    TAILWIND_OS=macos
fi
if [ "$(uname -m)" = x86_64 ]; then
    TAILWIND_ARCH=x64
else
    TAILWIND_ARCH=arm64
fi
TAILWIND_BUILD=$TAILWIND_OS-$TAILWIND_ARCH # Possible values: macos-x64, macos-arm64, linux-x64, linux-arm64

MAIN_NS=org.archimitra
SOFT_DEPLOY_FN=org.archimitra/on-save
DEPLOY_FROM=master
DEPLOY_TO=prod
SERVER=example.com
