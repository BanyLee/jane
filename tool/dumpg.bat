@echo off
setlocal
pushd %~dp0

luajit dumpg.lua ..\genbeans.lua

luajit dumpg.lua ..\port\csharp\genbeans_csharp.lua

luajit dumpg.lua ..\port\lua\src\genbeans_lua.lua
luajit dumpg.lua ..\port\lua\src\platform.lua
luajit dumpg.lua ..\port\lua\src\util.lua
luajit dumpg.lua ..\port\lua\src\stream.lua
luajit dumpg.lua ..\port\lua\src\bean.lua
luajit dumpg.lua ..\port\lua\src\test.lua

pause
