set options=%*
echo "Running XOP with options [[ %options% ]]"
echo %JAVA_HOME%
set PATH=%PATH%;.\jniLibs\x86
java -cp ".\xop-all.jar" -Djava.library.path="%JAVA_HOME%\jre\lib\ext;.\jniLibs\x86" %options% edu.drexel.xop.Run

