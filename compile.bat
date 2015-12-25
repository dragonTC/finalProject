@echo off
echo compileing...
javac *.java -cp ./src;lib/ezprivacy.jar;lib/netty.jar -d ./class
pause
