@echo off
echo compileing...
javac ./src/*.java -cp ./src;lib/ezprivacy.jar;lib/netty.jar -d ./class
echo done!
pause
